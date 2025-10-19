package rk.powermilk.cache

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.DurationUnit
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

    private val _stats = MutableStateFlow(CacheStats())
    val stats: StateFlow<CacheStats> = _stats.asStateFlow()

    // POPRAWKA: Przechowuj Job żeby móc go anulować
    private var cleanupJob: Job? = null

    init {
        startCleanup()
    }

    private fun startCleanup() {
        cleanupJob?.cancel()

        cleanupJob = scope.launch {
            try {
                while (isActive) {
                    delay(cleanupInterval)
                    cleanup()
                }
            } catch (e: CancellationException) {
                // graceful exit
            }
        }
    }

    fun put(key: K, value: V, ttl: Duration) {
        val expiryTime = System.currentTimeMillis() + ttl.inWholeMilliseconds
        entries[key] = Entry(value, expiryTime)
        updateStats()
    }

    fun get(key: K): V? {
        val now = System.currentTimeMillis()
        val entry = entries[key]

        return if (entry == null) {
            incrementMisses()
            null
        } else if (now > entry.expiryTime) {
            entries.remove(key)
            incrementEvictions()
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
            updateStats()
        }
    }

    fun clear() {
        entries.clear()
        _stats.value = CacheStats()
    }

    private fun cleanup() {
        val now = System.currentTimeMillis()
        val expiredKeys = entries.filter { (_, entry) ->
            entry.expiryTime < now
        }.keys.toList()

        expiredKeys.forEach { entries.remove(it) }

        if (expiredKeys.isNotEmpty()) {
            incrementEvictions(expiredKeys.size)
        }

        updateStats()
    }

    private fun incrementHits() {
        _stats.update { current ->
            val newHits = current.hits + 1
            val newTotal = newHits + current.misses
            current.copy(
                hits = newHits,
                hitRate = if (newTotal > 0) newHits.toDouble() / newTotal else 0.0
            )
        }
    }

    private fun incrementMisses() {
        _stats.update { current ->
            val newMisses = current.misses + 1
            val newTotal = current.hits + newMisses
            current.copy(
                misses = newMisses,
                hitRate = if (newTotal > 0) current.hits.toDouble() / newTotal else 0.0
            )
        }
    }

    private fun incrementEvictions(count: Int = 1) {
        _stats.update { it.copy(evictions = it.evictions + count) }
    }

    private fun updateStats() {
        _stats.update { current ->
            val total = current.hits + current.misses
            current.copy(
                size = entries.size,
                hitRate = if (total > 0) current.hits.toDouble() / total else 0.0
            )
        }
    }
}
