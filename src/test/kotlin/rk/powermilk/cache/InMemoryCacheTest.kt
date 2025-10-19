import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import rk.powermilk.cache.CacheStats
import rk.powermilk.cache.InMemoryCache
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class InMemoryCacheTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var cache: InMemoryCache<String, String>
    private lateinit var scope: TestScope

    @BeforeTest
    fun setup() {
        scope = TestScope(testDispatcher)
        cache = InMemoryCache(scope, cleanupInterval = 100.milliseconds)
    }

    @AfterTest
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `stores and retrieves values within TTL`() = scope.runTest {
        cache.put("a", "alpha", 200.milliseconds)
        assertEquals("alpha", cache.get("a"))
    }

    @Test
    fun `expired values are not returned`() = scope.runTest {
        cache.put("a", "alpha", 100.milliseconds)
        advanceTimeBy(150)
        assertNull(cache.get("a"))
    }

    @Test
    fun `cleanup removes expired entries`() = scope.runTest {
        cache.put("x", "val", 50.milliseconds)
        advanceTimeBy(200)
        // run cleanup tick
        advanceTimeBy(100)
        val stats = cache.stats.first()
        assertEquals(0, stats.size)
        assertTrue(stats.evictions > 0)
    }

    @Test
    fun `stats reflect hits and misses`() = scope.runTest {
        cache.put("a", "val", 200.milliseconds)
        cache.get("a") // hit
        cache.get("b") // miss

        val stats = cache.stats.first()
        assertEquals(1, stats.hits)
        assertEquals(1, stats.misses)
        assertEquals(1, stats.size)
    }

    @Test
    fun `stats flow emits updates`() = scope.runTest {
        val emissions = mutableListOf<CacheStats>()
        val job = launch { cache.stats.take(3).collect { emissions.add(it) } }

        cache.put("a", "1", 200.milliseconds)
        cache.get("a")
        cache.get("b")

        advanceUntilIdle()
        job.join()

        assertTrue(emissions.size >= 2)
        assertTrue(emissions.last().hits >= 1)
    }
}
