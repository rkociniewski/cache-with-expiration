# Git Hooks - Cheat Sheet 🚀

## ⚡ Quick Commands

```bash
# Installation
./setup-git-hooks.sh

# Reinstallation
./gradlew installGitHooks

# Bypass hooks (emergency!)
git commit --no-verify -m "message"
git push --no-verify

# Debug hook
bash -x .git/hooks/pre-commit

# Disable hook
mv .git/hooks/pre-commit .git/hooks/pre-commit.disabled

# Enable hook
mv .git/hooks/pre-commit.disabled .git/hooks/pre-commit
```

---

## 📝 Commit Message Format

```
<type>(<scope>): <description>

[optional body]

[optional footer]
```

### Types

| Type       | Usage         | Example                         |
|------------|---------------|---------------------------------|
| `feat`     | New feature   | `feat: Add dark mode`           |
| `fix`      | Bug fix       | `fix: Resolve crash on startup` |
| `docs`     | Documentation | `docs: Update README`           |
| `style`    | Formatting    | `style: Apply ktlint`           |
| `refactor` | Refactoring   | `refactor: Simplify auth logic` |
| `perf`     | Optimization  | `perf: Optimize image loading`  |
| `test`     | Tests         | `test: Add unit tests`          |
| `build`    | Build system  | `build: Update Gradle`          |
| `ci`       | CI/CD         | `ci: Update workflows`          |
| `chore`    | Other         | `chore: Update dependencies`    |

### Examples

```bash
✅ feat: Add user authentication
✅ feat(auth): Implement OAuth2 login
✅ fix: Resolve memory leak in Room
✅ fix(ui): Correct button alignment
✅ docs: Update API documentation
✅ test(integration): Add checkout tests
✅ ci: Add automated release workflow
✅ chore(deps): Bump Kotlin to 1.9.20

❌ Added new feature
❌ fix:Resolved bug
❌ Update
```

---

## 🔢 Version Bumping

### Semantic Versioning: MAJOR.MINOR.PATCH

```
1.2.3 → 1.2.4  (patch)   Bugfix, hotfix
1.2.3 → 1.3.0  (minor)   New feature
1.2.3 → 2.0.0  (major)   Breaking change
```

### Branch → Version Mapping

| Branch      | Version Bump | Example       |
|-------------|--------------|---------------|
| `hotfix/*`  | PATCH        | 1.2.3 → 1.2.4 |
| `release/*` | MINOR        | 1.2.3 → 1.3.0 |
| `feature/*` | (in release) | -             |
| `main`      | Bump both!   | version       |

### versionCode Strategies

```kotlin
// Strategy 1: Semantic
versionCode = MAJOR * 10000 + MINOR * 100 + PATCH
versionCode = 10203  // for 1.2.3

// Strategy 2: Incremental
versionCode = 1, 2, 3, 4, 5...

// Strategy 3: Timestamp
versionCode = 241010  // YYMMDD
```

---

## 🎯 Workflow Scenarios

### Hotfix Workflow

```bash
git checkout main
git checkout -b hotfix/1.2.4

# Change: version = "1.2.4"
git add build.gradle.kts
git commit -m "fix: Critical payment bug"
git push origin hotfix/1.2.4

# PR → main → merge → PR → develop
```

### Release Workflow

```bash
git checkout develop
git checkout -b release/2.0.0

# Change: version = "2.0.0"
git add build.gradle.kts
git commit -m "build: Bump version to 2.0.0"
git push origin release/2.0.0

# Testing → PR → main → tag → PR → develop
```

### Feature Workflow

```bash
git checkout develop
git checkout -b feature/user-profile

# Develop feature (DON'T change version!)
git commit -m "feat: Add profile screen"
git commit -m "feat: Add avatar upload"
git push origin feature/user-profile

# PR → develop (version changed later in release)
```

---

## 🔍 Pre-commit Checks

### What is checked?

| Check            | Blocks?    | Description                |
|------------------|------------|----------------------------|
| Debug statements | ⚠️ Warning | `println`, `Log.d`, `TODO` |
| Large files      | ❌ Error    | Files > 5MB                |
| Secrets          | ❌ Error    | API keys, passwords        |
| Merge conflicts  | ❌ Error    | `<<<<<<<` markers          |
| Code style       | ❌ Error    | ktlint (if installed)      |

### Bypass Checks

```bash
# Skip all
git commit --no-verify

# Skip only pre-commit
SKIP_PRE_COMMIT=1 git commit

# Skip specific check (edit hook)
```

---

## 🚨 Common Errors & Solutions

### Error: "Invalid commit message format"

```bash
# ❌ Error
git commit -m "Added feature"

# ✅ Fix
git commit -m "feat: Add feature"

# ✅ Or amend
git commit --amend -m "feat: Add feature"
```

### Error: "versionCode was not incremented"

```bash
# ❌ Error on push to main
git push origin main
# ERROR: versionCode was not incremented!
# Remote: 42, Local: 42

# ✅ Solution
# 1. Bump versionCode in build.gradle.kts
versionCode = 43

# 2. Amend commit
git add build.gradle.kts
git commit --amend --no-edit

# 3. Force push (carefully!)
git push origin main --force-with-lease
```

### Error: "Potential secret detected"

```bash
# ❌ Error
const val API_KEY = "sk_live_123456789"

# ✅ Solution 1: local.properties
# local.properties (DON'T commit!)
API_KEY=sk_live_123456789

# build.gradle.kts
val properties = Properties()
properties.load(FileInputStream(rootProject.file("local.properties")))
buildConfigField("String", "API_KEY", "\"${properties["API_KEY"]}\"")

# ✅ Solution 2: Environment variables
# BuildConfig
buildConfigField("String", "API_KEY", "\"${System.getenv("API_KEY")}\"")

# ✅ Solution 3: Secrets in CI/CD
# GitHub Secrets → use in workflows
```

### Warning: "Debug statements found"

```bash
# ⚠️ Warning (doesn't block)
System.out.println("Debug")
Log.d(TAG, "Debug")

# ✅ Solution 1: Remove
# Remove before committing

# ✅ Solution 2: Use Timber
Timber.d("Debug")  // OK in debug builds

# ✅ Solution 3: BuildConfig check
if (BuildConfig.DEBUG) {
    Log.d(TAG, "Debug")
}
```

### Error: "File too large"

```bash
# ❌ Error
# File too large: video.mp4 (8MB)

# ✅ Solution 1: Git LFS
git lfs install
git lfs track "*.mp4"
git add .gitattributes
git commit -m "chore: Add Git LFS tracking"

# ✅ Solution 2: Don't commit
# Add to .gitignore
echo "*.mp4" >> .gitignore

# ✅ Solution 3: Use CDN/Storage
# Store large files in Firebase Storage, S3, etc.
```

---

## 🔐 Secrets Management

### ❌ NEVER commit:

```kotlin
// ❌ Hardcoded secrets
const val API_KEY = "sk_live_123"
const val PASSWORD = "password123"
const val PRIVATE_KEY = "-----BEGIN RSA..."

// ❌ In comments
// TODO: Change API_KEY from "sk_test_123"

// ❌ In logs
Log.d(TAG, "Token: $authToken")
```

### ✅ Proper approach:

```kotlin
// ✅ BuildConfig
buildConfigField("String", "API_KEY", "\"${getApiKey()}\"")

// ✅ local.properties
val apiKey = getLocalProperty("API_KEY")

// ✅ Environment variables
val apiKey = System.getenv("API_KEY") ?: "default"

// ✅ Encrypted preferences
val encryptedSharedPrefs = EncryptedSharedPreferences.create(...)
```

### local.properties template:

```properties
# local.properties (add to .gitignore)
# Copy this to local.properties and fill in your values

## API Keys
API_KEY=your_api_key_here
MAPS_API_KEY=your_
