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

class ExpiringCache<K, V>(
    private val expirationTime: Duration = 5.toDuration(DurationUnit.MINUTES),
    private val cleanupInterval: Duration = 1.toDuration(DurationUnit.MINUTES),
    private val clockMillis: () -> Long = { System.currentTimeMillis() }
) {
    private data class CacheEntry<V>(val value: V, val createdAtMillis: Long)

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

    private fun isExpired(entry: CacheEntry<V>): Boolean {
        val now = clockMillis()
        return now - entry.createdAtMillis > expirationTime.inWholeMilliseconds
    }

    suspend fun put(key: K, value: V) {
        val now = clockMillis()
        mutex.withLock {
            val wasPresent = map.containsKey(key)
            map[key] = CacheEntry(value, now)
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
                evictions.incrementAndGet()
                misses.incrementAndGet()
                return null
            }
            hits.incrementAndGet()
            return entry.value
        }
    }

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
                // NIE rethrow - deferred już wie o cancellation
            } catch (e: Exception) {
                mutex.withLock {
                    inFlight.remove(key)
                }
                deferred.completeExceptionally(e)
                // NIE rethrow - deferred już wie o błędzie
            }
        }

        mutex.withLock {
            inFlight[key] = InFlightComputation(deferred, job)
        }

        return deferred.await()
    }

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

    fun getStats(): CacheStats {
        val h = hits.get()
        val m = misses.get()
        val t = h + m
        val hitRate = if (t == 0L) 0.0 else h.toDouble() / t.toDouble()
        return CacheStats(sizeCounter.get(), h, m, hitRate, evictions.get())
    }

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

    fun stopCleanup() {
        cleanupJob?.cancel()
        cleanupJob = null
    }
}
