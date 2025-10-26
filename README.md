# 🗄️ Kotlin Expiring Cache Library

[![version](https://img.shields.io/badge/version-1.0.14-yellow.svg)](https://semver.org)
[![Awesome Kotlin Badge](https://kotlin.link/awesome-kotlin.svg)](https://github.com/KotlinBy/awesome-kotlin)
[![Build](https://github.com/rkociniewski/cache-with-expiration/actions/workflows/main.yml/badge.svg)](https://github.com/rkociniewski/rosario/actions/workflows/main.yml)
[![CodeQL](https://github.com/rkociniewski/cache-with-expiration/actions/workflows/codeql.yml/badge.svg)](https://github.com/rkociniewski/rosario/actions/workflows/codeql.yml)
[![Dependabot Status](https://img.shields.io/badge/Dependabot-enabled-success?logo=dependabot)](https://github.com/rkociniewski/cache-with-expiration/network/updates)
[![codecov](https://codecov.io/gh/rkociniewski/cache-with-expiration/branch/main/graph/badge.svg)](https://codecov.io/gh/rkociniewski/rosario)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.21-blueviolet?logo=kotlin)](https://kotlinlang.org/)
[![Gradle](https://img.shields.io/badge/Gradle-9.1.0-blue?logo=gradle)](https://gradle.org/)
[![License: MIT](https://img.shields.io/badge/License-MIT-greem.svg)](https://opensource.org/licenses/MIT)

A high-performance, coroutine-based caching library for Kotlin with automatic expiration, reactive stats monitoring, and thread-safe operations.

## ✨ Features

* ⏱️ **Automatic expiration** with configurable TTL (Time To Live)
* 🔄 **Multiple cache implementations** for different use cases
* 🧵 **Thread-safe** with Kotlin coroutines and mutex-based synchronization
* 📊 **Reactive stats monitoring** using StateFlow
* 🚀 **Deduplication** of concurrent computations for the same key
* 🧹 **Automatic cleanup** of expired entries
* 🎯 **Per-key TTL support** for fine-grained control
* 📈 **Hit rate tracking** for cache performance monitoring

## 🎯 Cache Implementations

### ExpiringCache
Thread-safe cache with fixed TTL for all entries. Ideal for simple caching scenarios where all entries share the same expiration time.

**Features:**
- Fixed expiration time for all entries
- Automatic deduplication of concurrent `getOrCompute` calls
- Background cleanup of expired entries
- Thread-safe operations with mutex

### ExpiringCachePerKey
Advanced cache with per-key TTL configuration. Perfect for scenarios where different entries need different expiration times.

**Features:**
- Individual TTL per cache entry
- Per-key expiration control
- Same deduplication and thread-safety as `ExpiringCache`
- Flexible expiration strategies

### InMemoryCache
Reactive cache with StateFlow-based stats monitoring. Best for applications that need real-time cache performance insights.

**Features:**
- Reactive stats updates via StateFlow
- Observable cache metrics
- ConcurrentHashMap-based storage for high performance
- Built-in cleanup scheduler

## 📦 Installation

Add to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation("rk.powermilk:cache:1.0.13")
}
```

## 🚀 Quick Start

### Basic Usage with ExpiringCache

```kotlin
import rk.powermilk.cache.ExpiringCache
import kotlin.time.Duration.Companion.minutes

val cache = ExpiringCache<String, User>(
    expirationTime = 5.minutes,
    cleanupInterval = 1.minutes
)

// Put value
cache.put("user:123", User("John Doe"))

// Get value
val user = cache.get("user:123")

// Get or compute
val result = cache.getOrCompute("user:456", scope) {
    // This computation runs only once, even with concurrent requests
    fetchUserFromDatabase("456")
}

// Start automatic cleanup
cache.startCleanup(coroutineScope)

// Get statistics
val stats = cache.getStats()
println("Hit rate: ${stats.hitRate}")
```

### Per-Key TTL with ExpiringCachePerKey

```kotlin
import rk.powermilk.cache.ExpiringCachePerKey
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

val cache = ExpiringCachePerKey<String, Data>(
    defaultTtl = 1.hours
)

// Short-lived cache entry (5 minutes)
cache.put("session:abc", sessionData, ttl = 5.minutes)

// Long-lived cache entry (24 hours)
cache.put("config:main", configData, ttl = 24.hours)

// Use custom TTL in getOrCompute
val data = cache.getOrCompute("api:response", ttl = 30.minutes) {
    fetchFromApi()
}
```

### Reactive Monitoring with InMemoryCache

```kotlin
import rk.powermilk.cache.InMemoryCache
import kotlinx.coroutines.flow.collect

val cache = InMemoryCache<String, String>(
    scope = coroutineScope,
    cleanupInterval = 30.seconds
)

// Monitor cache stats reactively
launch {
    cache.stats.collect { stats ->
        println("Cache size: ${stats.size}")
        println("Hit rate: ${stats.hitRate}")
        println("Evictions: ${stats.evictions}")
    }
}

// Use the cache
cache.put("key1", "value1", ttl = 5.minutes)
val value = cache.get("key1")
```

## 🔧 Advanced Usage

### Concurrent Request Deduplication

```kotlin
// Multiple concurrent requests for the same key
// Only one computation will execute
val results = List(100) {
    async {
        cache.getOrCompute("expensive:key", this) {
            // This runs only once despite 100 concurrent calls
            performExpensiveComputation()
        }
    }
}.awaitAll()
```

### Custom Clock for Testing

```kotlin
var currentTime = 0L

val cache = ExpiringCache<String, Int>(
    expirationTime = 100.milliseconds,
    clockMillis = { currentTime }
)

cache.put("key", 42)

// Simulate time passing
currentTime += 101

// Entry is now expired
cache.get("key") // returns null
```

### Error Handling

```kotlin
try {
    val result = cache.getOrCompute("key", scope) {
        throw RuntimeException("Computation failed")
    }
} catch (e: RuntimeException) {
    // Handle error - cache remains consistent
    println("Computation failed: ${e.message}")
}
```

## 📊 Cache Statistics

All cache implementations provide detailed statistics:

```kotlin
val stats = cache.getStats()

println("Size: ${stats.size}")           // Current number of entries
println("Hits: ${stats.hits}")           // Number of cache hits
println("Misses: ${stats.misses}")       // Number of cache misses
println("Hit Rate: ${stats.hitRate}")    // Ratio of hits to total accesses
println("Evictions: ${stats.evictions}") // Number of evicted entries
```

## ⚙️ Configuration Options

### ExpiringCache Parameters

- **expirationTime**: Duration - Time after which entries expire (default: 5 minutes)
- **cleanupInterval**: Duration - Interval between cleanup runs (default: 1 minute)
- **clockMillis**: () -> Long - Custom clock for testing (default: System.currentTimeMillis)

### ExpiringCachePerKey Parameters

- **defaultTtl**: Duration - Default TTL for entries (default: 5 minutes)
- **cleanupInterval**: Duration - Interval between cleanup runs (default: 1 minute)
- **clockMillis**: () -> Long - Custom clock for testing (default: System.currentTimeMillis)

### InMemoryCache Parameters

- **scope**: CoroutineScope - Scope for cleanup coroutine
- **cleanupInterval**: Duration - Interval between cleanup runs (default: 30 seconds)

## 🗂 Project Structure

```
📦rk.powermilk.cache
 ┣ 📜CacheStats.kt              # Cache statistics data class
 ┣ 📜ExpiringCache.kt           # Fixed-TTL cache implementation
 ┣ 📜ExpiringCachePerKey.kt     # Per-key TTL cache implementation
 ┗ 📜InMemoryCache.kt           # Reactive cache with StateFlow
```

## 🛠️ Development

### Requirements

* Kotlin 2.2.21+
* Java 21+
* Gradle 9.1.0+

### Running Tests

```bash
# Run all tests
./gradlew test

# Run with coverage
./gradlew coverage

# Generate documentation
./gradlew dokka
```

### Code Quality

```bash
# Run detekt static analysis
./gradlew detekt

# Generate Jacoco coverage report
./gradlew jacocoTestReport
```

## 📈 Performance Characteristics

### Time Complexity

- **put**: O(1) - Constant time insertion
- **get**: O(1) - Constant time lookup
- **getOrCompute**: O(1) for cached values, O(computation) for misses
- **cleanup**: O(n) where n is the number of entries

### Thread Safety

All operations are thread-safe using:
- Kotlin coroutines with `Mutex` for `ExpiringCache` and `ExpiringCachePerKey`
- `ConcurrentHashMap` for `InMemoryCache`

### Memory Usage

- Each entry stores: key, value, and timestamp (typically 16-32 bytes overhead)
- In-flight computations tracked separately to prevent duplicate work

## 🧪 Testing

The library includes comprehensive test coverage:

- ✅ Basic put/get operations
- ✅ Expiration behavior
- ✅ Concurrent access patterns
- ✅ Statistics tracking
- ✅ Cleanup mechanism
- ✅ Error handling
- ✅ Thread safety

Current coverage: **75%+** (enforced by Jacoco)

## 📦 Dependencies

```kotlin
dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
    testImplementation("io.mockk:mockk")
}
```

## 🤝 Contributing

Contributions are welcome! Please:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/AmazingFeature`)
3. Write tests for your changes
4. Ensure all tests pass (`./gradlew test`)
5. Run code quality checks (`./gradlew detekt`)
6. Submit a Pull Request

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🏗️ Built With

* [Kotlin](https://kotlinlang.org/) - Programming language
* [Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-overview.html) - Asynchronous programming
* [Gradle](https://gradle.org/) - Build system
* [JUnit 5](https://junit.org/junit5/) - Testing framework
* [Detekt](https://detekt.dev/) - Static code analysis

## 📋 Versioning

We use [Semantic Versioning](http://semver.org/) for versioning.

Version format: `MAJOR.MINOR.PATCH`

- **MAJOR**: Breaking changes
- **MINOR**: New features (backward compatible)
- **PATCH**: Bug fixes

## 👨‍💻 Authors

* **Rafał Kociniewski** - [rkociniewski](https://github.com/rkociniewski)

## ⚡ Acknowledgments

* Inspired by modern caching patterns in distributed systems
* Built with Kotlin coroutines best practices
* Designed for high-performance applications

## 📚 Additional Resources

* [Kotlin Coroutines Guide](https://kotlinlang.org/docs/coroutines-guide.html)
* [Effective Caching Strategies](https://aws.amazon.com/caching/best-practices/)
* [Cache Invalidation Patterns](https://martinfowler.com/bliki/TwoHardThings.html)

---

Made with ❤️ and 🙏 by [Rafał Kociniewski](https://github.com/rkociniewski)
