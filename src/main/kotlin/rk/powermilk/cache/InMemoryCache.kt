package rk.powermilk.cache

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.TimeSource
import kotlin.time.toDuration

/**
 * In-memory cache with per-key TTL and reactive stats observation.
 */
class InMemoryCache<K, V>(
    private val scope: CoroutineScope,
    private val cleanupInterval: Duration = 30.toDuration(DurationUnit.SECONDS)
) {
    private data class Entry<V>(
        val value: V,
        val expiryTime: Long
    )

    private val entries = ConcurrentHashMap<K, Entry<V>>()

    // Metrics
    private val _stats = MutableStateFlow(CacheStats())
    val stats: StateFlow<CacheStats> = _stats.asStateFlow()

    private val timeSource = TimeSource.Monotonic

    init {
        // Periodic cleanup
        scope.launch {
            while (isActive) {
                delay(cleanupInterval)
                cleanup()
            }
        }
    }

    fun put(key: K, value: V, ttl: Duration) {
        val expiry = timeSource.markNow().plus(ttl).elapsedNow().inWholeMilliseconds
        entries[key] = Entry(value, System.currentTimeMillis() + ttl.inWholeMilliseconds)
        updateStats()
    }

    fun get(key: K): V? {
        val now = System.currentTimeMillis()
        val entry = entries[key]
        return if (entry == null || now > entry.expiryTime) {
            if (entry != null) {
                entries.remove(key)
                incrementEvictions()
            }
            incrementMisses()
            null
        } else {
            incrementHits()
            entry.value
        }
    }

    fun remove(key: K) {
        if (entries.remove(key) != null) {
            incrementEvictions()
        }
        updateStats()
    }

    fun clear() {
        entries.clear()
        updateStats()
    }

    private fun cleanup() {
        val now = System.currentTimeMillis()
        val expiredKeys = entries.filterValues { it.expiryTime < now }.keys
        expiredKeys.forEach { entries.remove(it) }
        if (expiredKeys.isNotEmpty()) incrementEvictions(expiredKeys.size)
        updateStats()
    }

    private fun incrementHits() {
        _stats.update { it.copy(hits = it.hits + 1) }
    }

    private fun incrementMisses() {
        _stats.update { it.copy(misses = it.misses + 1) }
    }

    private fun incrementEvictions(count: Int = 1) {
        _stats.update { it.copy(evictions = it.evictions + count) }
    }

    private fun updateStats() {
        _stats.update {
            it.copy(size = entries.size)
        }
    }
}

