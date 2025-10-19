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
import kotlin.collections.iterator
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class ExpiringCache<K, V>(
    private val expirationTime: Duration = 50.toDuration(DurationUnit.MILLISECONDS),
    private val cleanupInterval: Duration = 10.toDuration(DurationUnit.MILLISECONDS),
    // clock returns current time in millis; injectable for tests
    private val clockMillis: () -> Long = { System.currentTimeMillis() }
) {
    private data class CacheEntry<V>(val value: V, val createdAtMillis: Long)

    private val mutex = Mutex()
    private val map = mutableMapOf<K, CacheEntry<V>>()

    // Tracks concurrent computations to avoid duplicate compute() calls
    private val inFlight = mutableMapOf<K, CompletableDeferred<V>>()

    // Stats
    private val hits = AtomicLong(0)
    private val misses = AtomicLong(0)
    private val sizeCounter = AtomicInteger(0)

    // Cleanup job handle (if started)
    @Volatile
    private var cleanupJob: Job? = null

    private fun isExpired(entry: CacheEntry<V>): Boolean {
        val now = clockMillis()
        return now - entry.createdAtMillis > expirationTime.inWholeMilliseconds
    }

    /**
     * Put with automatic expiration (overwrites existing).
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
     * Get - returns null if not present or expired.
     */
    suspend fun get(key: K): V? {
        mutex.withLock {
            val entry = map[key]
            if (entry == null) {
                misses.incrementAndGet()
                return null
            }
            if (isExpired(entry)) {
                // remove expired entry
                map.remove(key)
                sizeCounter.decrementAndGet()
                misses.incrementAndGet()
                return null
            }
            hits.incrementAndGet()
            return entry.value
        }
    }

    /**
     * Get or compute: returns cached value if present & not expired,
     * otherwise computes using provided suspend compute() once (per key)
     * even when multiple coroutines call concurrently.
     */
    suspend fun getOrCompute(key: K, compute: suspend () -> V): V {
        // First, fast-path check under lock
        mutex.withLock {
            val entry = map[key]
            if (entry != null && !isExpired(entry)) {
                hits.incrementAndGet()
                return entry.value
            }
            // If expired, remove it now
            if (entry != null && isExpired(entry)) {
                map.remove(key)
                sizeCounter.decrementAndGet()
            }

            // If another coroutine is already computing the value, wait for it
            val waiting = inFlight[key]
            if (waiting != null) {
                // increment miss because we didn't have a usable cached value
                misses.incrementAndGet()
                return waiting.await()
            }

            // Otherwise create placeholder CompletableDeferred and put into inFlight
            val deferred = CompletableDeferred<V>()
            inFlight[key] = deferred
            // We'll release the lock and compute outside
        }

        // Compute outside the mutex so computation doesn't block other operations
        val deferred = mutex.withLock { inFlight[key]!! } // safe: we just inserted
        try {
            val value = compute()
            // store into cache under lock
            mutex.withLock {
                val wasPresent = map.containsKey(key)
                map[key] = CacheEntry(value, clockMillis())
                if (!wasPresent) sizeCounter.incrementAndGet()
            }
            deferred.complete(value)
            misses.incrementAndGet() // it's a miss because we had to compute
            return value
        } catch (e: Throwable) {
            // propagate exception to waiters and rethrow
            deferred.completeExceptionally(e)
            throw e
        } finally {
            // cleanup inFlight entry
            mutex.withLock {
                inFlight.remove(key)
            }
        }
    }

    /**
     * Start background cleanup in provided scope. Multiple calls: cancels previous job.
     * The cleanup job respects coroutine cancellation.
     */
    fun startCleanup(scope: CoroutineScope) {
        // Cancel existing job if present
        cleanupJob?.cancel()

        cleanupJob = scope.launch(start = CoroutineStart.LAZY) {
            // Use while (isActive) loop to honor cancellation
            try {
                while (isActive) {
                    delay(cleanupInterval)
                    cleanupExpired()
                }
            } catch (e: CancellationException) {
                // graceful exit
            }
        }.also { it.start() }
    }

    /**
     * Removes expired entries. Safe to call concurrently (suspend).
     */
    private suspend fun cleanupExpired() {
        val now = clockMillis()
        mutex.withLock {
            val iterator = map.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (now - entry.value.createdAtMillis > expirationTime.inWholeMilliseconds) {
                    iterator.remove()
                    sizeCounter.decrementAndGet()
                }
            }
        }
    }

    /**
     * Non-suspending snapshot of stats. Size is read from atomic counter to avoid suspension.
     */
    fun getStats(): CacheStats {
        val h = hits.get()
        val m = misses.get()
        val t = h + m
        val hitRate = if (t == 0L) 0.0 else h.toDouble() / t.toDouble()
        return CacheStats(sizeCounter.get(), h, m, hitRate)
    }

    /**
     * Clears all entries and cancels any in-flight computations.
     */
    suspend fun clear() {
        mutex.withLock {
            map.clear()
            sizeCounter.set(0)
            // cancel/complete any in-flight computations
            for ((_, deferred) in inFlight) {
                deferred.cancel(CancellationException("Cache cleared"))
            }
            inFlight.clear()
        }
    }

    /**
     * Stop the cleanup job if running.
     */
    fun stopCleanup() {
        cleanupJob?.cancel()
        cleanupJob = null
    }
}
