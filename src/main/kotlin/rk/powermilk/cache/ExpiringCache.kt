package rk.powermilk.cache

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

/**
 * A thread-safe, coroutine-based cache with automatic expiration and fixed TTL.
 *
 * This cache implementation provides:
 * - Automatic expiration of entries after a fixed time period
 * - Thread-safe operations using Kotlin coroutines and Mutex
 * - Deduplication of concurrent computations for the same key
 * - Background cleanup of expired entries
 * - Comprehensive statistics tracking (hits, misses, evictions, hit rate)
 *
 * All entries in this cache share the same expiration time. For per-key TTL
 * configuration, use [ExpiringCachePerKey] instead.
 *
 * ## Thread Safety
 * All operations are thread-safe and can be safely called from multiple coroutines
 * concurrently. The cache uses a mutex to synchronize access to internal state.
 *
 * ## Concurrent Computations
 * When multiple coroutines call [getOrCompute] for the same key simultaneously,
 * only one computation will be executed. All other coroutines will wait for and
 * receive the same result.
 *
 * @param K The type of keys maintained by this cache.
 * @param V The type of values stored in this cache.
 * @property expirationTime The duration after which cache entries expire.
 *                          Default is 5 minutes.
 * @property cleanupInterval The interval between automatic cleanup runs.
 *                           Default is 1 minute.
 * @property clockMillis A function that returns the current time in milliseconds.
 *                       Used primarily for testing. Default is [System.currentTimeMillis].
 *
 * @sample
 * ```kotlin
 * val cache = ExpiringCache<String, User>(
 *     expirationTime = 5.minutes,
 *     cleanupInterval = 1.minutes
 * )
 *
 * // Store a value
 * cache.put("user:123", User("John"))
 *
 * // Retrieve a value
 * val user = cache.get("user:123")
 *
 * // Get or compute with deduplication
 * val result = cache.getOrCompute("user:456", scope) {
 *     fetchUserFromDatabase("456")
 * }
 *
 * // Start automatic cleanup
 * cache.startCleanup(coroutineScope)
 *
 * // Get performance statistics
 * val stats = cache.getStats()
 * ```
 */
class ExpiringCache<K, V>(
    private val expirationTime: Duration = 5.toDuration(DurationUnit.MINUTES),
    private val cleanupInterval: Duration = 1.toDuration(DurationUnit.MINUTES),
    private val clockMillis: () -> Long = { System.currentTimeMillis() }
) {
    /**
     * Internal cache entry that stores the value along with creation timestamp.
     *
     * @property value The cached value.
     * @property createdAtMillis The timestamp when this entry was created (in milliseconds).
     */
    private data class CacheEntry<V>(val value: V, val createdAtMillis: Long)

    /**
     * Represents an in-flight computation for a specific key.
     *
     * @property deferred The deferred result of the computation.
     * @property job The coroutine job executing the computation.
     */
    private data class InFlightComputation<V>(
        val deferred: CompletableDeferred<V>,
        val job: Job
    )

    private val mutex = Mutex()
    private val map = mutableMapOf<K, CacheEntry<V>>()
    private val inFlight = mutableMapOf<K, InFlightComputation<V>>()

    private val hits = AtomicLong(0)
    private val misses = AtomicLong(0)
    private val evictions = AtomicLong(0)
    private val sizeCounter = AtomicInteger(0)

    @Volatile
    private var cleanupJob: Job? = null

    /**
     * Checks if a cache entry has expired based on the current time.
     *
     * @param entry The cache entry to check.
     * @return `true` if the entry has expired, `false` otherwise.
     */
    private fun isExpired(entry: CacheEntry<V>): Boolean {
        val now = clockMillis()
        return now - entry.createdAtMillis > expirationTime.inWholeMilliseconds
    }

    /**
     * Stores a value in the cache with the configured expiration time.
     *
     * If a value already exists for the given key, it will be replaced.
     * The entry will expire after [expirationTime] duration.
     *
     * @param key The cache key.
     * @param value The value to cache.
     */
    suspend fun put(key: K, value: V) {
        val now = clockMillis()
        mutex.withLock {
            val wasPresent = map.containsKey(key)
            map[key] = CacheEntry(value, now)
            if (!wasPresent) sizeCounter.incrementAndGet()
        }
    }

    /**
     * Retrieves a value from the cache.
     *
     * If the entry exists but has expired, it will be automatically removed
     * and `null` will be returned.
     *
     * @param key The cache key.
     * @return The cached value if present and not expired, `null` otherwise.
     */
    suspend fun get(key: K): V? {
        mutex.withLock {
            val entry = map[key]
            if (entry == null) {
                misses.incrementAndGet()
                return null
            }
            if (isExpired(entry)) {
                map.remove(key)
                sizeCounter.decrementAndGet()
                evictions.incrementAndGet()
                misses.incrementAndGet()
                return null
            }
            hits.incrementAndGet()
            return entry.value
        }
    }

    /**
     * Retrieves a value from the cache or computes it if absent or expired.
     *
     * This method guarantees that for concurrent calls with the same key,
     * the computation will be executed only once. All other coroutines will
     * wait for and receive the same result.
     *
     * If the computation fails with an exception, the exception will be
     * propagated to all waiting coroutines, and no value will be cached.
     *
     * ## Behavior
     * 1. If the value is cached and not expired, it is returned immediately (cache hit).
     * 2. If another coroutine is already computing the value, this call waits for the result.
     * 3. Otherwise, the computation is started and the result is cached.
     *
     * @param key The cache key.
     * @param scope The coroutine scope in which to execute the computation.
     * @param compute A suspend function that computes the value if not in cache.
     * @return The cached or computed value.
     * @throws CancellationException if the computation is cancelled.
     * @throws Exception if the computation fails.
     *
     * @sample
     * ```kotlin
     * val user = cache.getOrCompute("user:123", scope) {
     *     // This computation runs only once for concurrent calls
     *     database.fetchUser("123")
     * }
     * ```
     */
    @Suppress("TooGenericExceptionCaught")
    suspend fun getOrCompute(
        key: K,
        scope: CoroutineScope,
        compute: suspend () -> V
    ): V {
        val deferredToAwait: CompletableDeferred<V>?

        mutex.withLock {
            val entry = map[key]
            if (entry != null && !isExpired(entry)) {
                hits.incrementAndGet()
                return entry.value
            }

            if (entry != null && isExpired(entry)) {
                map.remove(key)
                sizeCounter.decrementAndGet()
                evictions.incrementAndGet()
            }

            val waiting = inFlight[key]
            if (waiting != null) {
                misses.incrementAndGet()
                deferredToAwait = waiting.deferred
            } else {
                deferredToAwait = null
            }
        }

        if (deferredToAwait != null) {
            return deferredToAwait.await()
        }

        val deferred = CompletableDeferred<V>()
        val job = scope.launch {
            try {
                val value = compute()

                mutex.withLock {
                    val wasPresent = map.containsKey(key)
                    map[key] = CacheEntry(value, clockMillis())
                    if (!wasPresent) sizeCounter.incrementAndGet()

                    inFlight.remove(key)
                }

                deferred.complete(value)
                misses.incrementAndGet()
            } catch (e: CancellationException) {
                mutex.withLock {
                    inFlight.remove(key)
                }
                deferred.cancel(e)
                // Do not rethrow - deferred already knows about cancellation
            } catch (e: Exception) {
                mutex.withLock {
                    inFlight.remove(key)
                }
                deferred.completeExceptionally(e)
                // Do not rethrow - deferred already knows about the error
            }
        }

        mutex.withLock {
            inFlight[key] = InFlightComputation(deferred, job)
        }

        return deferred.await()
    }

    /**
     * Starts automatic background cleanup of expired entries.
     *
     * The cleanup process runs periodically at the interval specified by
     * [cleanupInterval]. If cleanup is already running, the previous job
     * is cancelled before starting a new one.
     *
     * The cleanup job will continue running until:
     * - [stopCleanup] is called
     * - The provided [scope] is cancelled
     * - The cache is cleared
     *
     * @param scope The coroutine scope in which to run the cleanup job.
     *
     * @sample
     * ```kotlin
     * val cache = ExpiringCache<String, Data>()
     * cache.startCleanup(lifecycleScope)
     * // Cleanup runs automatically every cleanupInterval
     * ```
     */
    fun startCleanup(scope: CoroutineScope) {
        cleanupJob?.cancel()

        cleanupJob = scope.launch {
            try {
                while (isActive) {
                    delay(cleanupInterval)
                    cleanupExpired()
                }
            } catch (_: CancellationException) {
            }
        }
    }

    /**
     * Removes all expired entries from the cache.
     *
     * This method is called automatically by the background cleanup job
     * started via [startCleanup], but can also be called manually if needed.
     *
     * @see startCleanup
     */
    private suspend fun cleanupExpired() {
        val now = clockMillis()
        var evictedCount = 0

        mutex.withLock {
            val iterator = map.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (now - entry.value.createdAtMillis > expirationTime.inWholeMilliseconds) {
                    iterator.remove()
                    sizeCounter.decrementAndGet()
                    evictedCount++
                }
            }
        }

        if (evictedCount > 0) {
            evictions.addAndGet(evictedCount.toLong())
        }
    }

    /**
     * Returns current cache statistics.
     *
     * The statistics include:
     * - Current cache size
     * - Number of hits and misses
     * - Hit rate (ratio of hits to total accesses)
     * - Number of evictions
     *
     * @return A [CacheStats] object containing current cache metrics.
     *
     * @sample
     * ```kotlin
     * val stats = cache.getStats()
     * println("Cache performance:")
     * println("  Size: ${stats.size}")
     * println("  Hit rate: ${(stats.hitRate * 100).toInt()}%")
     * println("  Evictions: ${stats.evictions}")
     * ```
     */
    fun getStats(): CacheStats {
        val h = hits.get()
        val m = misses.get()
        val t = h + m
        val hitRate = if (t == 0L) 0.0 else h.toDouble() / t.toDouble()
        return CacheStats(sizeCounter.get(), h, m, hitRate, evictions.get())
    }

    /**
     * Removes all entries from the cache and cancels all in-flight computations.
     *
     * This method:
     * - Clears all cached entries
     * - Cancels all ongoing computations
     * - Resets the size counter to 0
     * - Does NOT reset statistics (hits, misses, evictions)
     *
     * Any coroutines waiting for in-flight computations will receive
     * a [CancellationException].
     *
     * @sample
     * ```kotlin
     * cache.clear()
     * // Cache is now empty, all pending computations are cancelled
     * ```
     */
    suspend fun clear() {
        mutex.withLock {
            map.clear()
            sizeCounter.set(0)

            for ((_, computation) in inFlight) {
                computation.job.cancel(CancellationException("Cache cleared"))
            }
            inFlight.clear()
        }
    }

    /**
     * Stops the automatic background cleanup job.
     *
     * After calling this method, expired entries will not be automatically
     * removed until [startCleanup] is called again. Entries will still be
     * checked for expiration during [get] and [getOrCompute] operations.
     *
     * @see startCleanup
     */
    fun stopCleanup() {
        cleanupJob?.cancel()
        cleanupJob = null
    }
}
