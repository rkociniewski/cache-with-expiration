package rk.powermilk.cache

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
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
 * A thread-safe, coroutine-based cache with per-key TTL (Time To Live) configuration.
 *
 * This cache implementation provides:
 * - Individual expiration time for each cache entry
 * - Thread-safe operations using Kotlin coroutines and Mutex
 * - Deduplication of concurrent computations for the same key
 * - Background cleanup of expired entries
 * - Comprehensive statistics tracking (hits, misses, evictions, hit rate)
 *
 * Unlike [ExpiringCache], this implementation allows each entry to have its own
 * expiration time, making it ideal for scenarios where different data types or
 * sources require different caching strategies.
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
 * ## Per-Key TTL
 * Each cache entry can have its own expiration time, allowing fine-grained control:
 * - Session data: 5 minutes
 * - User profiles: 1 hour
 * - Configuration: 24 hours
 * - API responses: 30 seconds
 *
 * @param K The type of keys maintained by this cache.
 * @param V The type of values stored in this cache.
 * @property defaultTtl The default duration for which cache entries remain valid.
 *                      Default is 5 minutes. Can be overridden per entry.
 * @property cleanupInterval The interval between automatic cleanup runs.
 *                           Default is 1 minute.
 * @property clockMillis A function that returns the current time in milliseconds.
 *                       Used primarily for testing. Default is [System.currentTimeMillis].
 *
 * @sample
 * ```kotlin
 * val cache = ExpiringCachePerKey<String, Data>(
 *     defaultTtl = 1.hours
 * )
 *
 * // Store with custom TTL
 * cache.put("session:abc", sessionData, ttl = 5.minutes)
 * cache.put("config:main", configData, ttl = 24.hours)
 *
 * // Get or compute with custom TTL
 * val apiData = cache.getOrCompute("api:response", ttl = 30.seconds) {
 *     fetchFromExternalApi()
 * }
 *
 * // Start automatic cleanup
 * cache.startCleanup(coroutineScope)
 * ```
 */
class ExpiringCachePerKey<K, V>(
    private val defaultTtl: Duration = 5.toDuration(DurationUnit.MINUTES),
    private val cleanupInterval: Duration = 1.toDuration(DurationUnit.MINUTES),
    private val clockMillis: () -> Long = { System.currentTimeMillis() }
) {
    /**
     * Internal cache entry that stores the value along with its absolute expiration time.
     *
     * @property value The cached value.
     * @property expiresAtMillis The absolute timestamp when this entry expires (in milliseconds).
     */
    private data class CacheEntry<V>(
        val value: V,
        val expiresAtMillis: Long
    )

    private val mutex = Mutex()
    private val map = mutableMapOf<K, CacheEntry<V>>()
    private val inFlight = mutableMapOf<K, CompletableDeferred<V>>()

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
    private fun isExpired(entry: CacheEntry<V>): Boolean =
        clockMillis() > entry.expiresAtMillis

    /**
     * Stores a value in the cache with a specified TTL.
     *
     * If a value already exists for the given key, it will be replaced along
     * with its expiration time. The entry will expire after the specified [ttl]
     * duration.
     *
     * @param key The cache key.
     * @param value The value to cache.
     * @param ttl The time-to-live duration for this entry. Defaults to [defaultTtl].
     *
     * @sample
     * ```kotlin
     * // Short-lived session data
     * cache.put("session:123", sessionData, ttl = 5.minutes)
     *
     * // Long-lived configuration
     * cache.put("config:app", configData, ttl = 24.hours)
     * ```
     */
    suspend fun put(key: K, value: V, ttl: Duration = defaultTtl) {
        val now = clockMillis()
        val expiresAt = now + ttl.inWholeMilliseconds

        mutex.withLock {
            val wasPresent = map.containsKey(key)
            map[key] = CacheEntry(value, expiresAt)
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
     * 3. Otherwise, the computation is started and the result is cached with the specified TTL.
     *
     * @param key The cache key.
     * @param ttl The time-to-live duration for the computed value. Defaults to [defaultTtl].
     * @param compute A suspend function that computes the value if not in cache.
     * @return The cached or computed value.
     * @throws Exception if the computation fails.
     *
     * @sample
     * ```kotlin
     * // API call with 30-second cache
     * val apiResponse = cache.getOrCompute("api:data", ttl = 30.seconds) {
     *     fetchFromApi()
     * }
     *
     * // Database query with 5-minute cache
     * val dbData = cache.getOrCompute("db:user:123", ttl = 5.minutes) {
     *     database.fetchUser("123")
     * }
     * ```
     */
    @Suppress("TooGenericExceptionCaught")
    suspend fun getOrCompute(
        key: K,
        ttl: Duration = defaultTtl,
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
                deferredToAwait = waiting
            } else {
                val deferred = CompletableDeferred<V>()
                inFlight[key] = deferred
                deferredToAwait = null
            }
        }

        if (deferredToAwait != null) {
            return deferredToAwait.await()
        }

        val result = try {
            val newValue = compute()
            val expiresAt = clockMillis() + ttl.inWholeMilliseconds

            mutex.withLock {
                val wasPresent = map.containsKey(key)
                map[key] = CacheEntry(newValue, expiresAt)
                if (!wasPresent) sizeCounter.incrementAndGet()

                // Complete deferred
                inFlight[key]?.complete(newValue)
                inFlight.remove(key)
            }

            misses.incrementAndGet()
            newValue
        } catch (e: Exception) {
            mutex.withLock {
                inFlight[key]?.completeExceptionally(e)
                inFlight.remove(key)
            }
            throw e
        }

        return result
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
     * val cache = ExpiringCachePerKey<String, Data>()
     * cache.startCleanup(lifecycleScope)
     * // Cleanup runs automatically every cleanupInterval
     * ```
     */
    fun startCleanup(scope: CoroutineScope) {
        cleanupJob?.cancel()

        cleanupJob = scope.launch(start = CoroutineStart.LAZY) {
            try {
                while (isActive) {
                    delay(cleanupInterval)
                    cleanupExpired()
                }
            } catch (_: CancellationException) {
            }
        }.also { it.start() }
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
                val (_, entry) = iterator.next()
                if (now > entry.expiresAtMillis) {
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
     * println("  Hits: ${stats.hits}, Misses: ${stats.misses}")
     * println("  Evictions: ${stats.evictions}")
     * ```
     */
    fun getStats(): CacheStats {
        val h = hits.get()
        val m = misses.get()
        val total = h + m
        val hitRate = if (total == 0L) 0.0 else h.toDouble() / total.toDouble()
        return CacheStats(
            size = sizeCounter.get(),
            hits = h,
            misses = m,
            hitRate = hitRate,
            evictions = evictions.get()
        )
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

            for ((_, deferred) in inFlight) {
                deferred.cancel(CancellationException("Cache cleared"))
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
