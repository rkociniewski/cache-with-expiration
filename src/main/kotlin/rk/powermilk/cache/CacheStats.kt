package rk.powermilk.cache

data class CacheStats(
    val size: Int = 0,
    val hits: Long = 0,
    val misses: Long = 0,
    val hitRate: Double = 0.0,
    val evictions: Long = 0,
)
