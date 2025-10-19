package rk.powermilk.cache

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class InMemoryCacheTest {

    @Test
    fun `put and get should work correctly`() = runTest {
        // POPRAWKA: Użyj backgroundScope
        val cache = InMemoryCache<String, Int>(backgroundScope)

        cache.put("key1", 100, ttl = 1.seconds)
        assertEquals(100, cache.get("key1"))
    }

    @Test
    fun `get should return null for non-existent key`() = runTest {
        val cache = InMemoryCache<String, Int>(backgroundScope)
        assertNull(cache.get("nonexistent"))
    }

    @Test
    fun `remove should remove entry and increment evictions`() = runTest {
        val cache = InMemoryCache<String, Int>(backgroundScope)

        cache.put("key1", 100, ttl = 1.seconds)
        assertEquals(100, cache.get("key1"))

        cache.remove("key1")

        assertNull(cache.get("key1"))
        assertEquals(1, cache.stats.value.evictions)
    }

    @Test
    fun `remove on non-existent key should do nothing`() = runTest {
        val cache = InMemoryCache<String, Int>(backgroundScope)

        val statsBefore = cache.stats.value
        cache.remove("nonexistent")
        val statsAfter = cache.stats.value

        assertEquals(statsBefore.evictions, statsAfter.evictions)
    }

    @Test
    fun `clear should remove all entries`() = runTest {
        val cache = InMemoryCache<String, Int>(backgroundScope)

        cache.put("key1", 1, ttl = 1.seconds)
        cache.put("key2", 2, ttl = 1.seconds)
        cache.put("key3", 3, ttl = 1.seconds)

        cache.clear()

        assertEquals(0, cache.stats.value.size)
        assertNull(cache.get("key1"))
        assertNull(cache.get("key2"))
        assertNull(cache.get("key3"))
    }

    @Test
    fun `clear should reset all stats`() = runTest {
        val cache = InMemoryCache<String, Int>(backgroundScope)

        cache.put("key1", 1, ttl = 1.seconds)
        cache.get("key1") // hit
        cache.get("key2") // miss

        cache.clear()

        val stats = cache.stats.value
        assertEquals(0, stats.size)
        assertEquals(0, stats.hits)
        assertEquals(0, stats.misses)
        assertEquals(0.0, stats.hitRate)
        assertEquals(0, stats.evictions)
    }

    @Test
    fun `stats should track hits correctly`() = runTest {
        val cache = InMemoryCache<String, Int>(backgroundScope)

        cache.put("key1", 100, ttl = 1.seconds)

        cache.get("key1") // hit
        cache.get("key1") // hit
        cache.get("key1") // hit

        assertEquals(3, cache.stats.value.hits)
    }

    @Test
    fun `stats should track misses correctly`() = runTest {
        val cache = InMemoryCache<String, Int>(backgroundScope)

        cache.get("key1") // miss
        cache.get("key2") // miss
        cache.get("key3") // miss

        assertEquals(3, cache.stats.value.misses)
    }

    @Test
    fun `stats should track hit rate correctly`() = runTest {
        val cache = InMemoryCache<String, Int>(backgroundScope)

        cache.put("key1", 100, ttl = 1.seconds)

        cache.get("key1") // hit
        cache.get("key1") // hit
        cache.get("key2") // miss

        val stats = cache.stats.value
        assertEquals(2, stats.hits)
        assertEquals(1, stats.misses)
        assertEquals(2.0 / 3.0, stats.hitRate, 0.001)
    }

    @Test
    fun `stats should track size correctly`() = runTest {
        val cache = InMemoryCache<String, Int>(backgroundScope)

        assertEquals(0, cache.stats.value.size)

        cache.put("key1", 1, ttl = 1.seconds)
        assertEquals(1, cache.stats.value.size)

        cache.put("key2", 2, ttl = 1.seconds)
        assertEquals(2, cache.stats.value.size)

        cache.remove("key1")
        assertEquals(1, cache.stats.value.size)
    }

    @Test
    fun `stats flow should emit updates reactively`() = runTest {
        val cache = InMemoryCache<String, Int>(backgroundScope)

        val statsUpdates = mutableListOf<CacheStats>()

        // Collect stats in background
        val collectJob = backgroundScope.launch {
            cache.stats.take(4).toList(statsUpdates)
        }

        delay(10)

        cache.put("key1", 1, ttl = 1.seconds)
        delay(10)

        cache.get("key1") // hit
        delay(10)

        cache.get("key2") // miss
        delay(10)

        collectJob.join()

        assertEquals(4, statsUpdates.size)
        // Initial state
        assertEquals(0, statsUpdates[0].size)
        // After put
        assertEquals(1, statsUpdates[1].size)
        // After hit
        assertEquals(1, statsUpdates[2].hits)
        // After miss
        assertEquals(1, statsUpdates[3].misses)
    }

    @Test
    fun `hitRate should be 0 when no operations`() = runTest {
        val cache = InMemoryCache<String, Int>(backgroundScope)
        assertEquals(0.0, cache.stats.value.hitRate, 0.001)
    }

    @Test
    fun `hitRate should be 1 with only hits`() = runTest {
        val cache = InMemoryCache<String, Int>(backgroundScope)
        cache.put("key1", 1, ttl = 1.seconds)

        cache.get("key1")
        cache.get("key1")
        cache.get("key1")

        assertEquals(1.0, cache.stats.value.hitRate, 0.001)
    }

    @Test
    fun `hitRate should be 0 with only misses`() = runTest {
        val cache = InMemoryCache<String, Int>(backgroundScope)

        cache.get("key1")
        cache.get("key2")
        cache.get("key3")

        assertEquals(0.0, cache.stats.value.hitRate, 0.001)
    }

    @Test
    fun `overwriting existing key should not change size`() = runTest {
        val cache = InMemoryCache<String, Int>(backgroundScope)

        cache.put("key1", 1, ttl = 1.seconds)
        assertEquals(1, cache.stats.value.size)

        cache.put("key1", 2, ttl = 1.seconds)
        assertEquals(1, cache.stats.value.size)
        assertEquals(2, cache.get("key1"))
    }

    @Test
    fun `concurrent gets should be thread-safe`() = runTest {
        val cache = InMemoryCache<String, Int>(backgroundScope)
        cache.put("key1", 100, ttl = 1.seconds)

        val jobs = List(1000) {
            launch {
                cache.get("key1")
            }
        }

        jobs.joinAll()

        assertEquals(1000, cache.stats.value.hits)
    }

    @Test
    fun `concurrent puts should be thread-safe`() = runTest {
        val cache = InMemoryCache<String, Int>(backgroundScope)

        val jobs = List(100) { i ->
            launch {
                cache.put("key$i", i, 1.seconds)
            }
        }

        jobs.joinAll()

        assertEquals(100, cache.stats.value.size)
    }

    @Test
    fun `stats should update after each operation`() = runTest {
        val cache = InMemoryCache<String, Int>(backgroundScope)

        // Initial state
        assertEquals(0, cache.stats.value.size)

        cache.put("key1", 1, 1.seconds)
        assertTrue(cache.stats.value.size > 0)

        cache.get("key1")
        assertTrue(cache.stats.value.hits > 0)

        cache.get("key2")
        assertTrue(cache.stats.value.misses > 0)
    }
}
