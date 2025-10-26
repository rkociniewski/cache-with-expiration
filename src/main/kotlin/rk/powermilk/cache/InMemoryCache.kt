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
 * A reactive, thread-safe cache with per-key TTL and observable statistics.
 *
 * This cache implementation provides:
 * - Individual expiration time for each cache entry
 * - Reactive statistics monitoring using Kotlin StateFlow
 * - Thread-safe operations using ConcurrentHashMap
 * - Background cleanup of expired entries
 * - Real-time performance metrics (hits, misses, evictions, hit rate)
 *
 * Unlike [ExpiringCache] and [ExpiringCachePerKey], this implementation uses
 * [ConcurrentHashMap] for storage and exposes statistics through a [StateFlow],
 * making it ideal for applications that need real-time cache performance monitoring
 * and reactive UI updates.
 *
 * ## Thread Safety
 * All operations are thread-safe. The cache uses [ConcurrentHashMap] for storage,
 * which provides lock-free reads and fine-grained locking for writes.
 *
 * ## Reactive Statistics
 * The [stats] property is a [StateFlow] that emits updates whenever cache
 * operations occur, enabling reactive monitoring and UI updates:
 *
 * ```kotlin
 * launch {
 *     cache.stats.collect { stats ->
 *         updateUI(stats)
 *     }
 * }
 * ```
 *
 * ## Automatic Cleanup
 * The cache automatically starts a background cleanup job upon initialization
 * that runs periodically to remove expired entries.
 *
 * @param K The type of keys maintained by this cache.
 * @param V The type of values stored in this cache.
 * @property scope The coroutine scope in which the cleanup job runs.
 * @property cleanupInterval The interval between automatic cleanup runs.
 *                           Default is 30 seconds.
 *
 * @sample
 * ```kotlin
 * val cache = InMemoryCache<String, User>(
 *     scope = viewModelScope,
 *     cleanupInterval = 30.seconds
 * )
 *
 * // Monitor stats reactively
 * launch {
 *     cache.stats.collect { stats ->
 *         println("Cache hit rate: ${(stats.hitRate * 100).toInt()}%")
 *     }
 * }
 *
 * // Store with custom TTL
 * cache.put("user:123", user, ttl = 5.minutes)
 *
 * // Retrieve value
 * val user = cache.get("user:123")
 * ```
 */
class InMemoryCache<K, V>(
    private val scope: CoroutineScope,
    private val cleanupInterval: Duration = 30.toDuration(DurationUnit.SECONDS)
) {
    /**
     * Internal cache entry that stores the value along with its absolute expiration time.
     *
     * @property value The cached value.
     * @property expiryTime The absolute timestamp when this entry expires (in milliseconds).
     */
    private data class Entry<V>(
        val value: V,
        val expiryTime: Long
    )

    private val entries = ConcurrentHashMap<K, Entry<V>>()

    private val _stats = MutableStateFlow(CacheStats())

    /**
     * A [StateFlow] that emits cache statistics updates.
     *
     * The flow emits a new [CacheStats] object whenever:
     * - An entry is added or removed
     * - A cache hit or miss occurs
     * - An entry is evicted (expired or manually removed)
     *
     * This enables reactive monitoring of cache performance in real-time.
     *
     * @sample
     * ```kotlin
     * // Collect stats in a coroutine
     * launch {
     *     cache.stats.collect { stats ->
     *         println("Size: ${stats.size}, Hit rate: ${stats.hitRate}")
     *     }
     * }
     *
     * // Or use in Compose UI
     * val stats by cache.stats.collectAsState()
     * Text("Cache size: ${stats.size}")
     * ```
     */
    val stats: StateFlow<CacheStats> = _stats.asStateFlow()

    private var cleanupJob: Job? = null

    init {
        startCleanup()
    }

    /**
     * Starts the automatic background cleanup job.
     *
     * The cleanup process runs periodically at the interval specified by
     * [cleanupInterval]. This method is called automatically during cache
     * initialization.
     *
     * If the cleanup job needs to be restarted, this method will cancel
     * any existing cleanup job before starting a new one.
     */
    private fun startCleanup() {
        cleanupJob?.cancel()

        cleanupJob = scope.launch {
            try {
                while (isActive) {
                    delay(cleanupInterval)
                    cleanup()
                }
            } catch (_: CancellationException) {
                // graceful exit
            }
        }
    }

    /**
     * Stores a value in the cache with a specified TTL.
     *
     * If a value already exists for the given key, it will be replaced along
     * with its expiration time. The entry will expire after the specified [ttl]
     * duration from the current time.
     *
     * This operation triggers a statistics update, emitting a new value to the
     * [stats] StateFlow.
     *
     * @param key The cache key.
     * @param value The value to cache.
     * @param ttl The time-to-live duration for this entry.
     *
     * @sample
     * ```kotlin
     * // Cache user session for 5 minutes
     * cache.put("session:abc", session, ttl = 5.minutes)
     *
     * // Cache configuration for 1 hour
     * cache.put("config:app", config, ttl = 1.hours)
     * ```
     */
    fun put(key: K, value: V, ttl: Duration) {
        val expiryTime = System.currentTimeMillis() + ttl.inWholeMilliseconds
        entries[key] = Entry(value, expiryTime)
        updateStats()
    }

    /**
     * Retrieves a value from the cache.
     *
     * If the entry exists but has expired, it will be automatically removed,
     * the eviction counter will be incremented, and `null` will be returned.
     *
     * This operation updates cache statistics:
     * - Increments hits if the value is found and not expired
     * - Increments misses if the value is not found or has expired
     * - Increments evictions if an expired entry is removed
     *
     * @param key The cache key.
     * @return The cached value if present and not expired, `null` otherwise.
     *
     * @sample
     * ```kotlin
     * val user = cache.get("user:123")
     * if (user != null) {
     *     // Use cached user
     * } else {
     *     // Fetch from database
     * }
     * ```
     */
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

    /**
     * Removes an entry from the cache.
     *
     * If the entry exists, it is removed and the eviction counter is incremented.
     * This operation triggers a statistics update.
     *
     * @param key The cache key to remove.
     *
     * @sample
     * ```kotlin
     * // Invalidate cached session
     * cache.remove("session:abc")
     * ```
     */
    fun remove(key: K) {
        if (entries.remove(key) != null) {
            incrementEvictions()
            updateStats()
        }
    }

    /**
     * Removes all entries from the cache and resets all statistics.
     *
     * This operation:
     * - Clears all cached entries
     * - Resets size to 0
     * - Resets hits, misses, and evictions to 0
     * - Resets hit rate to 0.0
     *
     * The cleanup job continues running and is not affected by this operation.
     *
     * @sample
     * ```kotlin
     * // Clear all cached data
     * cache.clear()
     * ```
     */
    fun clear() {
        entries.clear()
        _stats.value = CacheStats()
    }

    /**
     * Removes all expired entries from the cache.
     *
     * This method is called automatically by the background cleanup job.
     * It iterates through all entries and removes those whose expiration
     * time has passed.
     *
     * If any entries are removed, the eviction counter is incremented and
     * statistics are updated.
     */
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

    /**
     * Increments the hit counter and updates the hit rate.
     *
     * This method is called internally when a cache lookup succeeds
     * (the entry exists and has not expired).
     */
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

    /**
     * Increments the miss counter and updates the hit rate.
     *
     * This method is called internally when a cache lookup fails
     * (the entry doesn't exist or has expired).
     */
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

    /**
     * Increments the eviction counter by the specified count.
     *
     * This method is called internally when entries are removed due to
     * expiration or manual removal.
     *
     * @param count The number of evictions to add. Default is 1.
     */
    private fun incrementEvictions(count: Int = 1) {
        _stats.update { it.copy(evictions = it.evictions + count) }
    }

    /**
     * Updates the cache statistics with the current size and hit rate.
     *
     * This method is called after operations that might change the cache
     * size (put, remove, clear, cleanup).
     */
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
