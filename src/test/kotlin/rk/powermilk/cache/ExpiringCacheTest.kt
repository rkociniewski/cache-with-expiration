package rk.powermilk.cache

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.milliseconds

class ExpiringCacheTest {

    @Test
    fun `should store and retrieve value`() = runTest {
        val cache = ExpiringCache<String, Int>()

        cache.put("key1", 42)
        val result = cache.get("key1")

        assertEquals(42, result)
    }

    @Test
    fun `should return null for expired entries`() = runTest {
        var currentTime = 0L
        val cache = ExpiringCache<String, Int>(
            expirationTime = 100.milliseconds,
            clockMillis = { currentTime }
        )

        cache.put("key1", 42)
        assertEquals(42, cache.get("key1"))

        // Advance time past expiration
        currentTime += 101
        assertNull(cache.get("key1"))
    }

    @Test
    fun `getOrCompute should compute only once for concurrent requests`() = runTest {
        val cache = ExpiringCache<String, Int>()
        var computeCount = 0

        val results = (1..10).map {
            async {
                cache.getOrCompute("key1", this) {
                    delay(50)
                    computeCount++
                    42
                }
            }
        }.awaitAll()

        assertEquals(1, computeCount, "Compute should be called only once")
        assertEquals(List(10) { 42 }, results)
    }

    @Test
    fun `should track hits and misses correctly`() = runTest {
        val cache = ExpiringCache<String, Int>()

        // Miss - not in cache
        cache.getOrCompute("key1", this) { 42 }

        // Hit - in cache
        cache.get("key1")
        cache.get("key1")

        // Miss - not in cache
        cache.get("key2")

        val stats = cache.getStats()
        assertEquals(2, stats.hits)
        assertEquals(2, stats.misses)
        assertEquals(0.5, stats.hitRate)
    }

    @Test
    fun `should calculate hitRate correctly`() = runTest {
        val cache = ExpiringCache<String, Int>()

        assertEquals(0.0, cache.getStats().hitRate, "Empty cache should have 0.0 hit rate")

        cache.put("key1", 1)
        cache.get("key1") // hit
        cache.get("key1") // hit
        cache.get("key2") // miss

        val stats = cache.getStats()
        assertEquals(2, stats.hits)
        assertEquals(1, stats.misses)
        assertEquals(2.0 / 3.0, stats.hitRate, 0.001)
    }

    @Test
    fun `cleanup should remove expired entries`() = runTest {
        var currentTime = 0L
        val cache = ExpiringCache<String, Int>(
            100.milliseconds,
            50.milliseconds,
            { currentTime }
        )

        cache.put("key1", 1)
        cache.put("key2", 2)
        assertEquals(2, cache.getStats().size)

        cache.startCleanup(this)  // ✅ Użyj TestScope

        currentTime += 101
        delay(60)

        val stats = cache.getStats()
        assertEquals(0, stats.size)
        assertEquals(2, stats.evictions)

        cache.stopCleanup()  // ✅ Opcjonalnie: zatrzymaj przed końcem testu
    }

    @Test
    fun `clear should remove all entries and cancel in-flight computations`() = runTest {
        val cache = ExpiringCache<String, Int>()
        cache.put("key1", 1)
        cache.put("key2", 2)

        val deferred = async {
            try {
                cache.getOrCompute("key3", this) {
                    delay(1000)
                    3
                }
            } catch (_: CancellationException) {
                null
            }
        }

        delay(50)
        cache.clear()

        assertEquals(0, cache.getStats().size)
        assertNull(deferred.await())
    }

    @Test
    fun `should handle exceptions in compute lambda`() = runTest {
        val cache = ExpiringCache<String, Int>()

        val exception = RuntimeException("Compute failed")

        try {
            cache.getOrCompute("key1", this) {
                throw exception
            }
        } catch (e: RuntimeException) {
            assertEquals("Compute failed", e.message)
        }

        // Cache should be empty after failed computation
        assertNull(cache.get("key1"))
        assertEquals(0, cache.getStats().size)
    }

    @Test
    fun `concurrent puts should be thread-safe`() = runTest {
        val cache = ExpiringCache<Int, Int>()

        coroutineScope {
            repeat(100) { i ->
                launch {
                    cache.put(i, i * 2)
                }
            }
        }

        assertEquals(100, cache.getStats().size)
    }

    @Test
    fun `getOrCompute with expired entry should recompute`() = runTest {
        var currentTime = 0L
        val cache = ExpiringCache<String, Int>(
            expirationTime = 100.milliseconds,
            clockMillis = { currentTime }
        )

        var computeCount = 0

        // First compute
        cache.getOrCompute("key1", this) {
            computeCount++
            42
        }

        assertEquals(1, computeCount)

        // Advance time past expiration
        currentTime += 101

        // Should recompute
        val result = cache.getOrCompute("key1", this) {
            computeCount++
            99
        }

        assertEquals(99, result)
        assertEquals(2, computeCount)
    }
}
