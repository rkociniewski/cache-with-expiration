# Contributing

Thank you for your interest in contributing! 🙏

## 📋 Table of Contents

- [Code of Conduct](#code-of-conduct)
- [Getting Started](#getting-started)
- [Development Workflow](#development-workflow)
- [Commit Guidelines](#commit-guidelines)
- [Pull Request Process](#pull-request-process)
- [Coding Standards](#coding-standards)
- [Testing](#testing)

## 📜 Code of Conduct

This project follows a Code of Conduct that all contributors are expected to uphold. Please be respectful and
constructive in all interactions.

## 🚀 Getting Started

### Prerequisites

- Android Studio Hedgehog or later
- JDK 21
- Git
- Basic knowledge of Kotlin and Jetpack Compose

### Setup Development Environment

1. **Fork and Clone**
   ```bash
   # Fork the repo on GitHub, then:
   git clone https://github.com/YOUR_USERNAME/rosario.git
   cd rosario
   ```

2. **Install Git Hooks**
   ```bash
   ./scripts/setup-git-hooks.sh
   ```

3. **Open in Android Studio**
    - File → Open → Select project directory
    - Wait for Gradle sync

4. **Run the App**
   ```bash
   ./gradlew installDebug
   ```

## 🔄 Development Workflow

### Branch Strategy

We follow [Git Flow](https://nvie.com/posts/a-successful-git-branching-model/):

```
main          # Production releases
  ↑
release/*     # Release preparation
  ↑
develop       # Development branch
  ↑
feature/*     # New features
fix/*         # Bug fixes
hotfix/*      # Production hotfixes
```

### Creating a Feature Branch

```bash
# Update develop
git checkout develop
git pull origin develop

# Create feature branch
git checkout -b feature/your-feature-name

# Make changes...

# Commit (hooks will validate)
git commit -m "feat: Add your feature"

# Push
git push origin feature/your-feature-name
```

## 📝 Commit Guidelines

We use [Conventional Commits](https://www.conventionalcommits.org/):

### Format

```
<type>[optional scope]: <description>

[optional body]

[optional footer]
```

### Types

| Type       | Description      | Example                             |
|------------|------------------|-------------------------------------|
| `feat`     | New feature      | `feat: Add Divine Mercy prayer`     |
| `fix`      | Bug fix          | `fix: Resolve crash on rotation`    |
| `docs`     | Documentation    | `docs: Update README`               |
| `style`    | Code style       | `style: Apply ktlint formatting`    |
| `refactor` | Code refactoring | `refactor: Simplify ViewModel`      |
| `test`     | Tests            | `test: Add prayer navigation tests` |
| `chore`    | Maintenance      | `chore: Update dependencies`        |
| `ci`       | CI/CD            | `ci: Update workflows`              |

### Examples

```bash
# Simple commit
git commit -m "feat: Add French language support"

# With scope
git commit -m "fix(ui): Correct bead alignment on tablets"

# With body
git commit -m "feat: Add prayer history

- Save completed prayers
- Show history in settings
- Allow clearing history"

# Breaking change
git commit -m "feat!: Change settings storage format

BREAKING CHANGE: Settings now use Proto DataStore.
Users will need to reconfigure their preferences."
```

### Git Hooks

Pre-commit hooks automatically check:

- ✅ Commit message format
- ✅ No debug statements (except Logger)
- ✅ No secrets in code
- ✅ No large files
- ✅ No merge conflicts

To bypass (use sparingly):

```bash
git commit --no-verify -m "emergency fix"
```

## 🔀 Pull Request Process

### 1. Before Creating PR

```bash
# Ensure tests pass
./gradlew test
./gradlew detekt

# Update from develop
git checkout develop
git pull origin develop
git checkout feature/your-feature
git rebase develop

# Resolve conflicts if any
```

### 2. Create Pull Request

1. Push your branch to GitHub
2. Go to the repository on GitHub
3. Click "Compare & pull request"
4. Fill in the PR template:

```markdown
## Description

Brief description of changes

## Type of Change

- [ ] Bug fix
- [ ] New feature
- [ ] Breaking change
- [ ] Documentation update

## Testing

- [ ] Unit tests pass
- [ ] UI tests pass
- [ ] Manual testing done

## Screenshots (if UI changes)

[Add screenshots]

## Checklist

- [ ] Code follows style guidelines
- [ ] Self-reviewed code
- [ ] Commented complex code
- [ ] Updated documentation
- [ ] No new warnings
- [ ] Added tests
- [ ] All tests pass
```

### 3. PR Review Process

1. **Automated Checks**
    - Build must pass
    - CodeQL security scan must pass
    - All tests must pass

2. **Code Review**
    - At least 1 approval required
    - Address review comments
    - Keep PR scope focused

3. **Merge**
    - Squash and merge (preferred)
    - Delete branch after merge

## 💻 Coding Standards

### Kotlin Style

Follow [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html):

```kotlin
// ✅ Good
class PrayerViewModel @Inject constructor(
    private val repository: PrayerRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PrayerUiState())
    val uiState: StateFlow<PrayerUiState> = _uiState.asStateFlow()

    fun loadPrayer(type: PrayerType) {
        viewModelScope.launch {
            repository.getPrayer(type).collect { prayer ->
                _uiState.update { it.copy(prayer = prayer) }
            }
        }
    }
}

// ❌ Bad
class PrayerViewModel @Inject constructor(private val repository: PrayerRepository) : ViewModel() {
    var uiState = MutableStateFlow(PrayerUiState())
    fun loadPrayer(type: PrayerType) {
        viewModelScope.launch {
            repository.getPrayer(type).collect {
                uiState.value = uiState.value.copy(prayer = it)
            }
        }
    }
}
```

### Code Formatting

```bash
# Auto-format
./gradlew ktlintFormat

# Check style
./gradlew ktlintCheck
./gradlew detekt
```

### Naming Conventions

```kotlin
// Classes: PascalCase
class PrayerRepository
sealed class BeadType

// Functions: camelCase
fun loadPrayers()
fun navigateToNext()

// Constants: UPPER_SNAKE_CASE
const val MAX_BEADS = 59
const val DEFAULT_LANGUAGE = "en"

// Private properties: _camelCase (if mutable state)
private val _beads = MutableStateFlow<List<Bead>>(emptyList())
val beads: StateFlow<List<Bead>> = _beads.asStateFlow()

// Composables: PascalCase
@Composable
fun BeadView(bead: Bead)

@Composable
fun PrayerScreen()
```

### Documentation

```kotlin
/**
 * Manages the state and logic for the Rosary prayer.
 *
 * @property repository Repository for loading prayer data
 * @property settingsStore Store for user settings
 */
class RosaryViewModel @Inject constructor(
    private val repository: PrayerRepository,
    private val settingsStore: SettingsStore
) : ViewModel() {

    /**
     * Navigates to the next bead in the prayer sequence.
     * Skips beads without prayer text (prayerId = 0).
     */
    fun navigateNext() {
        // Implementation
    }
}
```

## 🧪 Testing

### Unit Tests

```kotlin
@Test
fun `navigateNext should move to next bead with prayer`() = runTest {
        // Given
        val viewModel = RosaryViewModel(fakeRepository, fakeSettings)

        // When
        viewModel.navigateNext()

        // Then
        assertEquals(1, viewModel.uiState.value.currentIndex)
    }
```

### UI Tests

```kotlin
@Test
fun testBeadNavigation() {
    composeTestRule.setContent {
        BeadView(bead = testBead)
    }

    composeTestRule
        .onNodeWithText("Our Father")
        .assertIsDisplayed()
}
```

### Running Tests

```bash
# All unit tests
./gradlew test

# Specific test
./gradlew test --tests PrayerViewModelTest

# UI tests (requires emulator)
./gradlew connectedCheck

# With coverage
./gradlew jacocoTestReport
```

## 🔒 Security

### Never Commit

- ❌ API keys or secrets
- ❌ Keystore files
- ❌ local.properties
- ❌ Personal data

### Use

- ✅ BuildConfig for keys
- ✅ local.properties (gitignored)
- ✅ Environment variables

```kotlin
// ❌ Bad
val API_KEY = "sk_live_abc123"

// ✅ Good
val API_KEY = BuildConfig.API_KEY
```

## 🐛 Reporting Bugs

### Bug Report Template

```markdown
**Describe the bug**
Clear description of the bug

**To Reproduce**

1. Go to '...'
2. Click on '...'
3. See error

**Expected behavior**
What should happen

**Screenshots**
If applicable

**Device Info:**

- Device: [e.g. Pixel 6]
- Android: [e.g. 13]
- App Version: [e.g. 1.0.0]

**Additional context**
Any other relevant information
```

## 💡 Feature Requests

### Feature Request Template

```markdown
**Is your feature request related to a problem?**
Description of the problem

**Describe the solution you'd like**
Clear description of desired feature

**Describe alternatives**
Alternative solutions considered

**Additional context**
Screenshots, mockups, etc.
```

## 📚 Resources

- [Kotlin Documentation](https://kotlinlang.org/docs/)
- [Jetpack Compose](https://developer.android.com/jetpack/compose)
- [Android Development](https://developer.android.com/)
- [Git Flow](https://nvie.com/posts/a-successful-git-branching-model/)
- [Conventional Commits](https://www.conventionalcommits.org/)

## ❓ Questions?

- Open a [Discussion](https://github.com/rkociniewski/rosario/discussions)
- Check existing [Issues](https://github.com/rkociniewski/rosario/issues)
- Read the [Documentation](docs/)

## 🙏 Thank You!

Your contributions make this project better for everyone. We appreciate your time and effort!

---

Happy coding! 🚀
