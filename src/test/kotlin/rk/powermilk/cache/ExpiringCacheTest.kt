package rk.powermilk.cache

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class ExpiringCacheTest {

    private lateinit var cache: ExpiringCache<String, String>

    @BeforeTest
    fun setup() {
        cache = ExpiringCache()
    }

    @Test
    fun `stores and retrieves value within TTL`() = runTest {
        cache.put("key", "value")
        assertEquals("value", cache.get("key"))
    }

    @Test
    fun `removes expired value after TTL`() = runTest {
        cache.put("key", "value")
        advanceTimeBy(2000)
        assertNull(cache.get("key"))
    }

    @Test
    fun `clear removes all entries`() = runTest {
        cache.put("a", "x")
        cache.put("b", "y")
        cache.clear()
        assertNull(cache.get("a"))
        assertNull(cache.get("b"))
    }

    @Test
    fun `replacing key resets TTL`() = runTest {
        cache.put("k", "old")
        advanceTimeBy(80)
        cache.put("k", "new")
        advanceTimeBy(80)
        assertEquals("new", cache.get("k"))
    }
}
