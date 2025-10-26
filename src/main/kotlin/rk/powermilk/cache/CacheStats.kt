package rk.powermilk.cache

/**
 * Represents statistical information about cache performance.
 *
 * This data class provides comprehensive metrics for monitoring and analyzing
 * cache behavior, including hit/miss rates, size, and eviction counts.
 *
 * @property size The current number of entries in the cache.
 * @property hits The total number of successful cache lookups (cache hits).
 * @property misses The total number of failed cache lookups (cache misses).
 * @property hitRate The ratio of hits to total accesses (hits + misses).
 *                   Value ranges from 0.0 (no hits) to 1.0 (all hits).
 * @property evictions The total number of entries that have been removed from
 *                     the cache due to expiration or manual removal.
 *
 * @sample
 * ```kotlin
 * val stats = cache.getStats()
 * println("Cache performance: ${stats.hitRate * 100}% hit rate")
 * println("Total entries: ${stats.size}")
 * ```
 */
data class CacheStats(
    val size: Int = 0,
    val hits: Long = 0,
    val misses: Long = 0,
    val hitRate: Double = 0.0,
    val evictions: Long = 0,
)
