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

class ExpiringCachePerKey<K, V>(
    private val defaultTtl: Duration = 5.toDuration(DurationUnit.MINUTES),
    private val cleanupInterval: Duration = 1.toDuration(DurationUnit.MINUTES),
    private val clockMillis: () -> Long = { System.currentTimeMillis() }
) {
    private data class CacheEntry<V>(
        val value: V,
        val expiresAtMillis: Long
    )

    private val mutex = Mutex()
    private val map = mutableMapOf<K, CacheEntry<V>>()
    private val inFlight = mutableMapOf<K, CompletableDeferred<V>>()

    private val hits = AtomicLong(0)
    private val misses = AtomicLong(0)
    private val sizeCounter = AtomicInteger(0)

    @Volatile
    private var cleanupJob: Job? = null

    private fun isExpired(entry: CacheEntry<V>): Boolean =
        clockMillis() > entry.expiresAtMillis

    suspend fun put(key: K, value: V, ttl: Duration = defaultTtl) {
        val now = clockMillis()
        val expiresAt = now + ttl.inWholeMilliseconds
        mutex.withLock {
            val wasPresent = map.containsKey(key)
            map[key] = CacheEntry(value, expiresAt)
            if (!wasPresent) sizeCounter.incrementAndGet()
        }
    }

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
                misses.incrementAndGet()
                return null
            }
            hits.incrementAndGet()
            return entry.value
        }
    }

    suspend fun getOrCompute(
        key: K,
        ttl: Duration = defaultTtl,
        compute: suspend () -> V
    ): V {
        mutex.withLock {
            val entry = map[key]
            if (entry != null && !isExpired(entry)) {
                hits.incrementAndGet()
                return entry.value
            }
            if (entry != null && isExpired(entry)) {
                map.remove(key)
                sizeCounter.decrementAndGet()
            }

            val waiting = inFlight[key]
            if (waiting != null) {
                misses.incrementAndGet()
                return waiting.await()
            }

            val deferred = CompletableDeferred<V>()
            inFlight[key] = deferred
        }

        val deferred = mutex.withLock { inFlight[key]!! }

        try {
            val newValue = compute()
            val expiresAt = clockMillis() + ttl.inWholeMilliseconds

            mutex.withLock {
                val wasPresent = map.containsKey(key)
                map[key] = CacheEntry(newValue, expiresAt)
                if (!wasPresent) sizeCounter.incrementAndGet()
            }

            deferred.complete(newValue)
            misses.incrementAndGet()
            return newValue
        } catch (e: Throwable) {
            deferred.completeExceptionally(e)
            throw e
        } finally {
            mutex.withLock {
                inFlight.remove(key)
            }
        }
    }

    fun startCleanup(scope: CoroutineScope) {
        cleanupJob?.cancel()

        cleanupJob = scope.launch(start = CoroutineStart.LAZY) {
            try {
                while (isActive) {
                    delay(cleanupInterval)
                    cleanupExpired()
                }
            } catch (e: CancellationException) {
                // graceful stop
            }
        }.also { it.start() }
    }

    private suspend fun cleanupExpired() {
        val now = clockMillis()
        mutex.withLock {
            val iterator = map.entries.iterator()
            while (iterator.hasNext()) {
                val (_, entry) = iterator.next()
                if (now > entry.expiresAtMillis) {
                    iterator.remove()
                    sizeCounter.decrementAndGet()
                }
            }
        }
    }

    fun getStats(): CacheStats {
        val h = hits.get()
        val m = misses.get()
        val total = h + m
        val hitRate = if (total == 0L) 0.0 else h.toDouble() / total.toDouble()
        return CacheStats(sizeCounter.get(), h, m, hitRate)
    }

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

    fun stopCleanup() {
        cleanupJob?.cancel()
        cleanupJob = null
    }
}
