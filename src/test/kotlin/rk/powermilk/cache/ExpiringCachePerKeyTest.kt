package rk.powermilk.cache

import kotlin.time.DurationUnit
import kotlin.time.toDuration
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class ExpiringCachePerKeyTest {

    private lateinit var cache: ExpiringCachePerKey<String, String>

    @BeforeTest
    fun setup() {
        cache = ExpiringCachePerKey()
    }

    @Test
    fun `stores values with different TTLs`() = runTest {
        cache.put("short", "1", 100.toDuration(DurationUnit.MILLISECONDS))
        cache.put("long", "2", 1000.toDuration(DurationUnit.MILLISECONDS))

        advanceTimeBy(150)
        assertNull(cache.get("short"))
        assertEquals("2", cache.get("long"))
    }

    @Test
    fun `expired entries are removed`() = runTest {
        cache.put("k", "v", 50.toDuration(DurationUnit.MILLISECONDS))
        advanceTimeBy(100)
        assertNull(cache.get("k"))
    }

    @Test
    fun `removing key deletes only that entry`() = runTest {
        cache.put("a", "x", 500.toDuration(DurationUnit.MILLISECONDS))
        cache.put("b", "y", 500.toDuration(DurationUnit.MILLISECONDS))

        assertNull(cache.get("a"))
        assertEquals("y", cache.get("b"))
    }

    @Test
    fun `clear removes all entries`() = runTest {
        cache.put("a", "x", 500.toDuration(DurationUnit.MILLISECONDS))
        cache.put("b", "y", 500.toDuration(DurationUnit.MILLISECONDS))
        cache.clear()
        assertNull(cache.get("a"))
        assertNull(cache.get("b"))
    }
}
