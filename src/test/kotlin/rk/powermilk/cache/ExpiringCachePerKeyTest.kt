package rk.powermilk.cache

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

class ExpiringCachePerKeyTest {

    @Test
    fun `put and get should work correctly`() = runTest {
        val cache = ExpiringCachePerKey<String, Int>()
        cache.put("key1", 100)
        assertEquals(100, cache.get("key1"))
    }

    @Test
    fun `get should return null for non-existent key`() = runTest {
        val cache = ExpiringCachePerKey<String, Int>()
        assertNull(cache.get("nonexistent"))
    }

    @Test
    fun `get should return null for expired entry`() = runTest {
        var currentTime = 0L
        val cache = ExpiringCachePerKey<String, Int>(
            defaultTtl = 100.milliseconds,
            clockMillis = { currentTime }
        )

        cache.put("key1", 100, ttl = 100.milliseconds)
        assertEquals(100, cache.get("key1"))

        // Advance time past expiration
        currentTime += 101
        assertNull(cache.get("key1"))
    }

    @Test
    fun `put should support per-key TTL`() = runTest {
        var currentTime = 0L
        val cache = ExpiringCachePerKey<String, Int>(
            defaultTtl = 1.minutes,
            clockMillis = { currentTime }
        )

        cache.put("short", 1, ttl = 50.milliseconds)
        cache.put("long", 2, ttl = 200.milliseconds)

        // After 51ms, short should be expired
        currentTime += 51
        assertNull(cache.get("short"))
        assertEquals(2, cache.get("long"))

        // After 201ms, both should be expired
        currentTime = 201
        assertNull(cache.get("long"))
    }

    @Test
    fun `getOrCompute should compute value when not present`() = runTest {
        val cache = ExpiringCachePerKey<String, Int>()
        var computeCount = 0

        val result = cache.getOrCompute("key1") {
            computeCount++
            42
        }

        assertEquals(42, result)
        assertEquals(1, computeCount)
        assertEquals(42, cache.get("key1"))
    }

    @Test
    fun `getOrCompute should return cached value when present`() = runTest {
        val cache = ExpiringCachePerKey<String, Int>()
        var computeCount = 0

        cache.put("key1", 100)

        val result = cache.getOrCompute("key1") {
            computeCount++
            42
        }

        assertEquals(100, result)
        assertEquals(0, computeCount) // Should not compute
    }

    @Test
    fun `getOrCompute should recompute when entry expired`() = runTest {
        var currentTime = 0L
        val cache = ExpiringCachePerKey<String, Int>(
            defaultTtl = 100.milliseconds,
            clockMillis = { currentTime }
        )

        cache.put("key1", 100, ttl = 100.milliseconds)
        assertEquals(100, cache.get("key1"))

        // Expire the entry
        currentTime += 101

        var computeCount = 0
        val result = cache.getOrCompute("key1") {
            computeCount++
            200
        }

        assertEquals(200, result)
        assertEquals(1, computeCount)
    }

    @Test
    fun `getOrCompute should only compute once for concurrent calls`() = runTest {
        val cache = ExpiringCachePerKey<String, Int>()
        var computeCount = 0

        val jobs = List(100) {
            async {
                cache.getOrCompute("key1") {
                    delay(10)
                    computeCount++
                    42
                }
            }
        }

        val results = jobs.map { it.await() }

        assertEquals(1, computeCount) // Computed only once
        assertTrue(results.all { it == 42 })
    }

    @Test
    fun `getOrCompute should support per-key TTL`() = runTest {
        var currentTime = 0L
        val cache = ExpiringCachePerKey<String, Int>(
            defaultTtl = 1.minutes,
            clockMillis = { currentTime }
        )

        val result = cache.getOrCompute("key1", ttl = 50.milliseconds) {
            100
        }

        assertEquals(100, result)
        assertEquals(100, cache.get("key1"))

        currentTime += 51
        assertNull(cache.get("key1"))
    }

    @Test
    fun `should handle exceptions in compute lambda`() = runTest {
        val cache = ExpiringCachePerKey<String, Int>()
        val exception = RuntimeException("Compute failed")

        try {
            cache.getOrCompute("key1") {
                throw exception
            }
            fail("Should have thrown exception")
        } catch (e: RuntimeException) {
            assertEquals("Compute failed", e.message)
        }

        // Cache should be empty after failed computation
        assertNull(cache.get("key1"))
        assertEquals(0, cache.getStats().size)
    }

    @Test
    fun `cleanup should remove expired entries`() = runTest {
        var currentTime = 0L
        val cache = ExpiringCachePerKey<String, Int>(
            defaultTtl = 100.milliseconds,
            cleanupInterval = 50.milliseconds,
            clockMillis = { currentTime }
        )

        cache.put("key1", 1, ttl = 100.milliseconds)
        cache.put("key2", 2, ttl = 100.milliseconds)
        assertEquals(2, cache.getStats().size)

        cache.startCleanup(this)

        // Advance time and trigger cleanup
        currentTime += 101
        delay(60) // Wait for cleanup to run

        val stats = cache.getStats()
        assertEquals(0, stats.size)
        assertEquals(2, stats.evictions)

        cache.stopCleanup()
    }

    @Test
    fun `cleanup should not remove non-expired entries`() = runTest {
        var currentTime = 0L
        val cache = ExpiringCachePerKey<String, Int>(
            defaultTtl = 200.milliseconds,
            cleanupInterval = 50.milliseconds,
            clockMillis = { currentTime }
        )

        cache.put("key1", 1, ttl = 100.milliseconds)
        cache.put("key2", 2, ttl = 200.milliseconds)

        cache.startCleanup(this)

        // Advance time - only key1 should expire
        currentTime += 101
        delay(60)

        assertNull(cache.get("key1"))
        assertEquals(2, cache.get("key2"))
        assertEquals(1, cache.getStats().size)

        cache.stopCleanup()
    }

    @Test
    fun `stats should track hits and misses correctly`() = runTest {
        val cache = ExpiringCachePerKey<String, Int>()

        cache.put("key1", 100)

        cache.get("key1") // hit
        cache.get("key1") // hit
        cache.get("key2") // miss

        val stats = cache.getStats()
        assertEquals(2, stats.hits)
        assertEquals(1, stats.misses)
        assertEquals(2.0 / 3.0, stats.hitRate, 0.001)
    }

    @Test
    fun `stats should track size correctly`() = runTest {
        val cache = ExpiringCachePerKey<String, Int>()

        assertEquals(0, cache.getStats().size)

        cache.put("key1", 1)
        assertEquals(1, cache.getStats().size)

        cache.put("key2", 2)
        assertEquals(2, cache.getStats().size)

        cache.get("key1")
        assertEquals(2, cache.getStats().size)
    }

    @Test
    fun `stats should track evictions`() = runTest {
        var currentTime = 0L
        val cache = ExpiringCachePerKey<String, Int>(
            defaultTtl = 100.milliseconds,
            clockMillis = { currentTime }
        )

        cache.put("key1", 1, ttl = 100.milliseconds)

        currentTime += 101
        cache.get("key1") // Should evict

        assertEquals(1, cache.getStats().evictions)
    }

    @Test
    fun `clear should remove all entries`() = runTest {
        val cache = ExpiringCachePerKey<String, Int>()

        cache.put("key1", 1)
        cache.put("key2", 2)
        cache.put("key3", 3)

        assertEquals(3, cache.getStats().size)

        cache.clear()

        assertEquals(0, cache.getStats().size)
        assertNull(cache.get("key1"))
        assertNull(cache.get("key2"))
        assertNull(cache.get("key3"))
    }

    @Test
    fun `stopCleanup should cancel cleanup job`() = runTest {
        var currentTime = 0L
        val cache = ExpiringCachePerKey<String, Int>(
            defaultTtl = 100.milliseconds,
            cleanupInterval = 50.milliseconds,
            clockMillis = { currentTime }
        )

        cache.put("key1", 1, ttl = 100.milliseconds)
        cache.startCleanup(this)

        // Stop cleanup before it runs
        cache.stopCleanup()

        currentTime += 101
        delay(60)

        // Entry should still be there (cleanup was stopped)
        assertEquals(1, cache.getStats().size)
    }

    @Test
    fun `startCleanup multiple times should cancel previous job`() = runTest {
        var currentTime = 0L
        val cache = ExpiringCachePerKey<String, Int>(
            defaultTtl = 100.milliseconds,
            cleanupInterval = 50.milliseconds,
            clockMillis = { currentTime }
        )

        cache.startCleanup(this)
        cache.startCleanup(this) // Should cancel previous
        cache.startCleanup(this) // Should cancel previous

        // Should still work
        cache.put("key1", 1, ttl = 100.milliseconds)
        currentTime += 101
        delay(60)

        assertEquals(0, cache.getStats().size)
        cache.stopCleanup()
    }

    @Test
    fun `overwriting existing key should not change size`() = runTest {
        val cache = ExpiringCachePerKey<String, Int>()

        cache.put("key1", 1)
        assertEquals(1, cache.getStats().size)

        cache.put("key1", 2) // Overwrite
        assertEquals(1, cache.getStats().size)
        assertEquals(2, cache.get("key1"))
    }

    @Test
    fun `getOrCompute on expired entry should increment evictions`() = runTest {
        var currentTime = 0L
        val cache = ExpiringCachePerKey<String, Int>(
            defaultTtl = 100.milliseconds,
            clockMillis = { currentTime }
        )

        cache.put("key1", 1, ttl = 100.milliseconds)
        assertEquals(0, cache.getStats().evictions)

        currentTime += 101

        cache.getOrCompute("key1") { 2 }
        assertEquals(1, cache.getStats().evictions)
    }

    @Test
    fun `hitRate should be 0 when no operations`() = runTest {
        val cache = ExpiringCachePerKey<String, Int>()
        assertEquals(0.0, cache.getStats().hitRate, 0.001)
    }

    @Test
    fun `hitRate should be 1 with only hits`() = runTest {
        val cache = ExpiringCachePerKey<String, Int>()
        cache.put("key1", 1)

        cache.get("key1")
        cache.get("key1")
        cache.get("key1")

        assertEquals(1.0, cache.getStats().hitRate, 0.001)
    }

    @Test
    fun `hitRate should be 0 with only misses`() = runTest {
        val cache = ExpiringCachePerKey<String, Int>()

        cache.get("key1")
        cache.get("key2")
        cache.get("key3")

        assertEquals(0.0, cache.getStats().hitRate, 0.001)
    }
}
