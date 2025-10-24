# Git Hooks - Usage Examples

## 📋 Table of Contents

1. [Installation](#installation)
2. [Commit Message Examples](#commit-message-examples)
3. [Versioning Workflow](#versioning-workflow)
4. [Scenarios and Solutions](#scenarios-and-solutions)
5. [Intellij Idea Integration](#intellij-idea-integration)

---

## Installation

### Method 1: Automatic (via Gradle) - Configuration Cache Compatible

Add to `build.gradle.kts` (root):

```kotlin
// Option A: Use shell script (Recommended - Simple)
tasks.register<Exec>("installGitHooks") {
    description = "Install Git hooks"
    group = "git hooks"

    commandLine("bash", "setup-git-hooks.sh")
    onlyIf { file("setup-git-hooks.sh").exists() }
    isIgnoreExitValue = true
}

// Auto-install on first build
tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn("installGitHooks")
}

// Option B: Custom task (More control, configuration cache safe)
import org . gradle . api . DefaultTask
    import org . gradle . api . file . DirectoryProperty
    import org . gradle . api . tasks . *

    abstract class InstallGitHooksTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val scriptsDir: DirectoryProperty

    @get:OutputDirectory
    abstract val hooksDir: DirectoryProperty

    @TaskAction
    fun installHooks() {
        val scriptsDirFile = scriptsDir.get().asFile
        val hooksDirFile = hooksDir.get().asFile

        if (!hooksDirFile.exists() || !scriptsDirFile.exists()) {
            println("⚠️  Hooks directories not found")
            return
        }

        listOf("commit-msg", "pre-commit", "prepare-commit-msg", "post-commit", "pre-push").forEach { hook ->
            val source = File(scriptsDirFile, hook)
            val target = File(hooksDirFile, hook)

            if (source.exists()) {
                target.delete()
                source.setExecutable(true)

                try {
                    ProcessBuilder("ln", "-sf", "../../.githooks/$hook", target.absolutePath)
                        .start().waitFor()
                    println("✅ Installed $hook")
                } catch (e: Exception) {
                    source.copyTo(target, overwrite = true)
                    target.setExecutable(true)
                    println("✅ Copied $hook")
                }
            }
        }
    }
}

tasks.register<InstallGitHooksTask>("installGitHooks") {
    scriptsDir.set(layout.projectDirectory.dir(".githooks"))
    hooksDir.set(layout.projectDirectory.dir(".git/hooks"))
}
```

Then:

```bash
./gradlew build  # Automatically installs hooks
# Or manually: ./gradlew installGitHooks
```

### Method 2: Manual

```bash
# 1. Download setup script
curl -o setup-git-hooks.sh https://raw.githubusercontent.com/your-repo/setup-git-hooks.sh

# 2. Make executable
chmod +x setup-git-hooks.sh

# 3. Run
./setup-git-hooks.sh
```

### Method 3: Copy Hooks Manually

```bash
# Copy all hooks
cp .githooks/* .git/hooks/

# Make executable
chmod +x .git/hooks/*
```

---

## Commit Message Examples

### ✅ Valid Commits

```bash
# New feature
git commit -m "feat: Add user profile screen"
git commit -m "feat(auth): Implement OAuth2 login"

# Bug fix
git commit -m "fix: Resolve crash on app startup"
git commit -m "fix(database): Prevent memory leak in Room"

# CI/CD changes
git commit -m "ci: Update GitHub Actions workflows"
git commit -m "ci(android): Add automatic APK signing"

# Documentation
git commit -m "docs: Update README with setup instructions"
git commit -m "docs(api): Add JSDoc comments to API service"

# Refactoring
git commit -m "refactor: Simplify authentication logic"
git commit -m "refactor(ui): Extract common UI components"

# Tests
git commit -m "test: Add unit tests for LoginViewModel"
git commit -m "test(integration): Add e2e tests for checkout flow"

# Performance
git commit -m "perf: Optimize image loading with Coil"
git commit -m "perf(database): Add indexes to frequently queried columns"

# Build changes
git commit -m "build: Update Gradle to 8.5"
git commit -m "build(deps): Bump Kotlin to 1.9.20"

# Style/formatting
git commit -m "style: Apply ktlint formatting"
git commit -m "style(ui): Update color palette"

# Chore (maintenance)
git commit -m "chore: Update .gitignore"
git commit -m "chore(deps): Update dependencies"
```

### ❌ Invalid Commits

```bash
# Missing type
git commit -m "Added new feature"
# ❌ ERROR: Invalid commit message format!

# Missing space after colon
git commit -m "feat:Add feature"
# ❌ ERROR: Invalid commit message format!

# Invalid type
git commit -m "added: New feature"
# ❌ ERROR: Invalid commit message format!

# Description too short
git commit -m "fix: fix"
# ❌ ERROR: Description too short
```

### 📝 Extended Commit Messages

```bash
# With body and footer
git commit -m "feat: Add dark mode support

- Implement theme switching
- Add dark color palette
- Save user preference in SharedPreferences

Closes #123"

# With breaking change
git commit -m "feat!: Migrate to Jetpack Compose

BREAKING CHANGE: Removed all XML layouts.
Apps using custom views will need to be refactored."

# With multiple issues
git commit -m "fix: Resolve multiple crash scenarios

- Fixed NPE in UserRepository
- Added null checks in ProfileViewModel
- Improved error handling

Fixes #456
Fixes #457
Related to #458"
```

---

## Versioning Workflow

### Scenario 1: Production Hotfix

```bash
# 1. Create hotfix branch
git checkout main
git pull
git checkout -b hotfix/1.2.4

# 2. Fix the bug
# ... edit code ...

# 3. Bump version (patch only!)
# build.gradle.kts:
# version = "1.2.4" (was "1.2.3")

# 4. Commit
git add .
git commit -m "fix: Resolve critical payment bug"

# 5. Push - hook will verify version
git push origin hotfix/1.2.4
# ✅ Version check passed
# version bumped (patch)
# 1.2.3 → 1.2.4

# 6. Create PR to main
# 7. After merge, create PR to develop
```

### Scenario 2: Release Branch

```bash
# 1. Create release branch
git checkout develop
git pull
git checkout -b release/2.0.0

# 2. Bump version (minor bump)
# build.gradle.kts:
# version = "2.0.0" (was "1.9.5")

# 3. Commit
git add .
git commit -m "build: Bump version to 2.0.0"

# 4. Push - hook will verify consistency
git push origin release/2.0.0
# ⚠️  Warning: Version mismatch with branch name
# Branch: release/2.0.0
# Version: 2.0.0
# ✅ Versions match!

# 5. Testing, bugfixes (patches only)
# 6. Merge to main
# 7. Tag in main (automatic via CI)
# 8. Merge to develop
```

### Scenario 3: Feature Branch

```bash
# 1. Create feature branch
git checkout develop
git checkout -b feature/user-notifications

# 2. Develop feature
# ... multiple commits ...
git commit -m "feat: Add notification permission request"
git commit -m "feat: Implement push notification handler"
git commit -m "test: Add tests for notification service"

# 3. DON'T bump version in feature branch!
# Version will be bumped when merging to develop/release

# 4. Push
git push origin feature/user-notifications

# 5. Create PR to develop
# Hook doesn't require version bump for feature branches
```

### Scenario 4: Develop Branch

```bash
# 1. Merge feature to develop
git checkout develop
git merge feature/user-notifications

# 2. Bump version (optional, depends on strategy)
# Strategy A: Version 1.x.x-dev
# version = "1.10.0-dev"

# Strategy B: Don't change until release
# Keep as is, change only in release branch

# 3. Push
git push origin develop
```

---

## Scenarios and Solutions

### Problem: "Hook blocks my commit"

**Scenario:** Emergency fix, hook blocks commit

```bash
# Solution 1: Bypass hooks (document reason!)
git commit --no-verify -m "hotfix: Critical production fix - bypassing hooks"

# Solution 2: Fix the issue and commit normally
# Better approach - find what's blocking and fix it

# Solution 3: Temporarily disable specific hook
mv .git/hooks/pre-commit .git/hooks/pre-commit.disabled
git commit -m "fix: Critical fix"
mv .git/hooks/pre-commit.disabled .git/hooks/pre-commit
```

### Problem: "Forgot to bump version"

```bash
# Hook will catch it before push!
git push origin main
# ❌ ERROR: versionCode was not incremented!

# Solution:
# 1. Bump version locally
# 2. Amend last commit
git add build.gradle.kts
git commit --amend --no-edit

# 3. Force push (carefully!)
git push origin main --force-with-lease
```

### Problem: "Incorrectly formatted commit message"

```bash
# Hook blocks:
git commit -m "Added new feature"
# ❌ Invalid commit message format!

# Solution 1: Fix message
git commit -m "feat: Add new feature"

# Solution 2: Amend after committing with --no-verify
git commit --no-verify -m "Added new feature"
git commit --amend -m "feat: Add new feature"
```

### Problem: "Debug statement in code"

```bash
# Hook warns:
git commit -m "feat: Add logging"
# ⚠️  Warning: Debug statements found
# src/main/kotlin/Utils.kt:42: System.out.println("Debug")

# Solution 1: Remove debug statements
# Remove println, console.log, etc.

# Solution 2: Leave as warning (hook doesn't block)
# It's just a warning, you can continue

# Solution 3: Use proper logger
// Instead of:
println("Debug message")

// Use:
Log.d(TAG, "Debug message")  // Hook will catch this too!

// Or:
Timber.d("Debug message")    // OK, this is a framework
```

### Problem: "Secret detected in code"

```bash
# Hook blocks:
git commit -m "feat: Add API integration"
# ❌ Potential secret detected!
# Pattern: api_key = "..."

# Solution:
# 1. Remove secret from code
# 2. Add to local.properties
echo 'API_KEY="your-key-here"' >> local.properties

# 3. Add local.properties to .gitignore
echo 'local.properties' >> .gitignore

# 4. Use in build.gradle.kts
application {
    defaultConfig {
        val properties = Properties()
        properties.load(FileInputStream(rootProject.file("local.properties")))
        buildConfigField("String", "API_KEY", "\"${properties["API_KEY"]}\"")
    }
}

# 5. Commit
git commit -m "feat: Add API integration with secure key storage"
```

---

## Intellij Idea Integration

### Commit Message Template

**File → Settings → Version Control → Commit**

```
<type>(<scope>): <subject>

<body>

<footer>
```

Shortcuts:

- `Ctrl+K` - Commit
- `Ctrl+Shift+K` - Commit and Push

### Pre-commit in Intellij Idea

1. **Settings → Version Control → Commit**
2. Check boxes:
    - ✅ Analyze code
    - ✅ Check TODO
    - ✅ Optimize imports
    - ✅ Reformat code
    - ✅ Rearrange code

### Live Templates for Commits

**Settings → Editor → Live Templates**

```xml
<!-- Template: "commitfeat" -->
    feat($SCOPE$): $DESCRIPTION$

    $BODY$

    $END$

    <!-- Template: "commitfix" -->
    fix($SCOPE$): $DESCRIPTION$

    Fixes #$ISSUE$

    $END$
```

### Run Hooks in Intellij Idea

Add External Tools:

**Settings → Tools → External Tools → Add**

```
Name: Run Pre-commit Hook
Program: bash
Arguments: .git/hooks/pre-commit
Working directory: $ProjectFileDir$
```

Shortcut: Add in **Keymap** → External Tools

---

## 📊 Versioning Strategy

### Semantic Versioning (MAJOR.MINOR.PATCH)

```
1.0.0 → Initial release
1.0.1 → Bug fixes (hotfix)
1.1.0 → New features (release)
2.0.0 → Breaking changes (major release)
```

### versionCode Strategy

**Option 1: Semantic**

```kotlin
// versionCode = MAJOR * 10000 + MINOR * 100 + PATCH
versionCode = 1 * 10000 + 2 * 100 + 3 = 10203  // 1.2.3
versionCode = 2 * 10000 + 0 * 100 + 0 = 20000  // 2.0.0
```

**Option 2: Incremental**

```kotlin
versionCode = 1, 2, 3, 4, 5...
```

**Option 3: Timestamp**

```kotlin
// YYMMDD
versionCode = 241010  // October 10, 2024
```

### Pre-release Versions

```kotlin
version = "1.2.3-alpha.1"   // Alpha
version = "1.2.3-beta.2"    // Beta
version = "1.2.3-rc.1"      // Release Candidate
version = "1.2.3"           // Production
```

---

## 🎯 Best Practices Summary

### DO ✅

1. Always use Conventional Commits
2. Bump version before merging to main
3. Test locally before pushing
4. Use scope in commit messages
5. Add issue numbers in footer
6. Document breaking changes
7. Commit often, small changes

### DON'T ❌

1. Don't use `--no-verify` without reason
2. Don't commit secrets
3. Don't forget to bump version
4. Don't mix changes in one commit
5. Don't commit to main without review
6. Don't use unclear descriptions
7. Don't commit untested code

---

## 📚 Additional Tools

### Commitment - Interactive Commit Messages

```bash
npm install -g commitizen
commitizen init cz-conventional-changelog --save-dev --save-exact

# Usage
git cz  # Instead of git commit
```

### Husky - Better Git Hooks Management

```bash
npm install husky --save-dev
npx husky install
npx husky add .husky/pre-commit "npm test"
npx husky add .husky/commit-msg 'npx --no -- commitlint --edit "$1"'
```

### Commitlint - Validate Commit Messages

```bash
npm install --save-dev @commitlint/cli @commitlint/config-conventional

echo "module.exports = {extends: ['@commitlint/config-conventional']}" > commitlint.config.js
```

---

## 🆘 Troubleshooting

### Hook doesn't work after pull

```bash
# Reinstall hooks
./setup-git-hooks.sh

# Or manually
chmod +x .git/hooks/*
```

### Hook runs too slowly

```bash
# Disable heavy checks
# Edit .githooks/pre-commit
# Comment out: detekt, lint, etc.
```

### Hook conflicts with IDE

```bash
# Intellij Idea may run its own pre-commit
# Settings → Version Control → Commit
# Uncheck unnecessary options
```

---

Have questions? See [GIT_HOOKS.md](GIT_HOOKS.md) for full documentation!
