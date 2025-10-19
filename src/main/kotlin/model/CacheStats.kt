package model

data class CacheStats(
    val size: Int,
    val hits: Long,
    val misses: Long,
    val hitRate: Double
)
