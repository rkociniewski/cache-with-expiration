# Git Hooks - Frequently Asked Questions

## 🔧 Installation & Setup

### Q: How do I install the hooks?

**A:** Three methods:

```bash
# Method 1: Manual script
./setup-git-hooks.sh

# Method 2: Via Gradle
./gradlew installGitHooks

# Method 3: Automatic on build (if configured)
./gradlew build
```

### Q: I get "Configuration cache problems" error in Gradle

**A:** Update your `build.gradle.kts` to use configuration cache compatible task:

```kotlin
// Use Exec task instead of doLast
tasks.register<Exec>("installGitHooks") {
    commandLine("bash", "setup-git-hooks.sh")
    onlyIf { file("setup-git-hooks.sh").exists() }
    isIgnoreExitValue = true
}
```

Or use the custom `InstallGitHooksTask` from the examples.

### Q: Hooks don't work after git pull

**A:** Reinstall hooks after pulling:

```bash
./setup-git-hooks.sh
# or
./gradlew installGitHooks
```

### Q: Can I disable auto-installation?

**A:** Yes, remove this from `build.gradle.kts`:

```kotlin
// Remove or comment out:
tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn("installGitHooks")
}
```

Then install manually when needed: `./gradlew installGitHooks`

---

## 📝 Commit Messages

### Q: My commit message is rejected, but I don't understand why

**A:** Check the format:

```bash
# ❌ Wrong - missing type
git commit -m "Updated code"

# ✅ Correct
git commit -m "refactor: Update code structure"

# Common mistakes:
# - Missing colon after type: "feat Add feature" ❌
# - No space after colon: "feat:Add feature" ❌
# - Invalid type: "updated: Fix bug" ❌
# - Too short: "fix: fix" ❌
```

### Q: What if I'm merging or reverting?

**A:** Merge and revert commits are automatically allowed:

```bash
# These bypass validation:
git merge feature-branch  # "Merge branch 'feature-branch'"
git revert abc123         # "Revert 'feat: Add feature'"
```

### Q: Can I use breaking changes notation?

**A:** Yes! Use `!` or `BREAKING CHANGE:`:

```bash
# Method 1: Exclamation mark
git commit -m "feat!: Remove deprecated API"

# Method 2: Footer
git commit -m "feat: New authentication system

BREAKING CHANGE: Old auth methods are no longer supported"
```

### Q: How do I add issue numbers?

**A:** Two ways:

```bash
# Method 1: Footer (manual)
git commit -m "fix: Resolve payment bug

Fixes #123
Closes #456"

# Method 2: Automatic (if branch has issue number)
# Branch: feature/PROJ-123-user-login
git commit -m "feat: Add login screen"
# Result: "[PROJ-123] feat: Add login screen"
```

---

## 🔢 Versioning

### Q: When should I bump the version?

**A:**

- **Hotfix branches**: Before merging to main (patch bump: 1.2.3 → 1.2.4)
- **Release branches**: When creating the branch (minor bump: 1.2.3 → 1.3.0)
- **Feature branches**: Never! Version is bumped in release branch
- **Main branch**: Always ensure version is bumped before merge

### Q: Can I use pre-release versions?

**A:** Yes! Use semantic versioning extensions:

```kotlin
version = "2.0.0-alpha.1"   // Alpha testing
version = "2.0.0-beta.2"    // Beta testing
version = "2.0.0-rc.1"      // Release candidate
version = "2.0.0"           // Production release
```

---

## 🔐 Secrets & Security

### Q: Hook detected a "secret" but it's not sensitive

**A:** Common false positives:

```kotlin
// ❌ Triggers detection
val const API_URL = "https://api.example.com/v1/secret-path"
val testPassword = "password123"  // Test constant

// ✅ Solutions:
// 1. Rename to avoid keywords
val const API_URL = "https://api.example.com/v1/private-path"
val const TEST_AUTH = "test123"

// 2. Use BuildConfig
buildConfigField("String", "API_URL", "\"...\"")

// 3. Bypass for this commit (document why!)
git commit --no-verify -m "test: Add test constants"
```

### Q: Where should I store actual secrets?

**A:**

```properties
# 1. local.properties (gitignored)
API_KEY=your_actual_key_here
DB_PASSWORD=your_password

# 2. Environment variables
export API_KEY="your_key"

# 3. CI/CD Secrets (GitHub/GitLab)
# Settings → Secrets → Actions
```

### Q: How do I use secrets from local.properties?

**A:**

```kotlin
// build.gradle.kts
application {
    defaultConfig {
        // Load properties
        val properties = Properties()
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            properties.load(FileInputStream(localPropertiesFile))
        }

        // Use in BuildConfig
        buildConfigField(
            "String",
            "API_KEY",
            "\"${properties.getProperty("API_KEY", "")}\""
        )

        // Use in Manifest
        manifestPlaceholders["MAPS_API_KEY"] =
            properties.getProperty("MAPS_API_KEY", "")
    }
}

// Usage in code
val apiKey = BuildConfig.API_KEY
```

---

## 🚫 Bypassing Hooks

### Q: When is it OK to bypass hooks?

**A:** Only in emergencies:

✅ **OK to bypass:**

- Critical production bug that needs immediate fix
- CI/CD pipeline is broken and needs urgent repair
- Reverting a breaking change

❌ **NOT OK to bypass:**

- "I'm in a hurry"
- "Tests take too long"
- "I'll fix it later"
- "It's just a small change"

### Q: How do I bypass hooks?

**A:**

```bash
# Bypass all hooks
git commit --no-verify -m "hotfix: Critical fix"
git push --no-verify

# Document why you bypassed!
# In commit message or team chat:
# "Emergency production fix - bypassing hooks.
#  Will create follow-up PR to add proper tests."
```

### Q: How do I temporarily disable a specific hook?

**A:**

```bash
# Method 1: Rename
mv .git/hooks/pre-commit .git/hooks/pre-commit.disabled
# ... do your work ...
mv .git/hooks/pre-commit.disabled .git/hooks/pre-commit

# Method 2: Environment variable
SKIP_PRE_COMMIT=1 git commit -m "..."

# Method 3: Edit hook to add bypass condition
# Add to top of .githooks/pre-commit:
if [ -n "$SKIP_PRE_COMMIT" ]; then
    exit 0
fi
```

---

## 🐛 Debugging & Troubleshooting

### Q: Hook fails but doesn't show why

**A:** Run in debug mode:

```bash
# See what the hook is doing
bash -x .git/hooks/pre-commit

# Or check the hook script
cat .git/hooks/pre-commit

# Run hook manually
bash .git/hooks/pre-commit
```

### Q: Hook works locally but fails in CI

**A:** Check:

1. **Permissions**: CI might not have hooks installed
   ```yaml
   # In GitHub Actions, hooks run via checkout, not git hooks
   # Pre-commit checks should be in CI workflow steps
   ```

2. **File permissions**: Ensure scripts are executable
   ```yaml
   - name: Make hooks executable
     run: chmod +x .githooks/*
   ```

3. **Environment differences**: CI might have different tools
   ```bash
   # Check if tool exists before using
   if command -v ktlint &> /dev/null; then
       ktlint --check
   fi
   ```

### Q: Windows users can't run hooks

**A:** Options:

1. **Use Git Bash** (comes with Git for Windows)
2. **WSL** (Windows Subsystem for Linux)
3. **Copy instead of symlink**:
   ```bash
   # In setup script, detect OS and copy on Windows
   if [[ "$OSTYPE" == "msys" || "$OSTYPE" == "win32" ]]; then
       cp .githooks/* .git/hooks/
   fi
   ```

### Q: Hooks slow down my workflow

**A:** Optimize:

```bash
# 1. Disable slow checks in .githooks/pre-commit
# Comment out:
# ./gradlew detekt  # Takes 30 seconds
# ./gradlew lint    # Takes 1 minute

# 2. Run only on changed files
if git diff --cached --name-only | grep -E '\.kt$'; then
    ktlint $(git diff --cached --name-only --diff-filter=ACM | grep '\.kt$')
fi

# 3. Run heavy checks in CI only
# Move detekt, full test suite to GitHub Actions
```

---

##maps_key_here

## OAuth

OAUTH_CLIENT_ID=your_client_id
OAUTH_CLIENT_SECRET=your_client_secret

## Database

DB_PASSWORD=your_db_password

```

---

## 🎯 Best Practices

### Q: Should hooks block commits or just warn?

**A:** It depends:

**Block (return exit 1):**
- Secrets in code
- Merge conflict markers
- Invalid commit message format
- Version not bumped (on main/release)

**Warn (return exit 0):**
- Debug statements (might be intentional)
- Large files (might need Git LFS)
- Code style issues (might be work in progress)

### Q: How often should I update hooks?

**A:**

- **Review quarterly**: Check if rules still make sense
- **Update when needed**: New security requirements, team decisions
- **Communicate changes**: Let team know before updating
- **Version hooks**: Tag or note in CHANGELOG when changing rules

### Q: Should I enforce hooks team-wide?

**A:** Recommendations:

**DO enforce:**
- Commit message format
- No secrets in code
- Version bumping on main
- No merge conflicts

**DON'T enforce too strictly:**
- Code style (linters can auto-fix)
- Test coverage (CI can check)
- Documentation (review can catch)

Balance between automation and flexibility!

---

## 🤝 Team Collaboration

### Q: New team member can't commit

**A:** Onboarding checklist:

```bash
# 1. Clone repo
git clone <repo-url>

# 2. Install hooks
cd <project>
./setup-git-hooks.sh

# 3. Test hook
git commit --allow-empty -m "test: Verify hooks work"

# 4. If fails, check:
chmod +x .git/hooks/*
ls -la .git/hooks/
```

### Q: How do I propose changes to hooks?

**A:**

1. **Discuss with team** first
2. **Create PR** with hook changes
3. **Update documentation**
4. **Announce** in team chat
5. **Team reinstalls**: `./setup-git-hooks.sh`

### Q: Different projects, different rules?

**A:** Yes! Customize per project:

```bash
# Project A: Strict (production app)
# - Enforce all checks
# - Block on any issue
# - Require version bump

# Project B: Relaxed (internal tool)
# - Warn only
# - Allow bypass
# - Optional version bump

# Customize .githooks/* per project
```

---

## 📚 Advanced Topics

### Q: Can I add custom checks?

**A:** Yes! Edit `.githooks/pre-commit`:

```bash
# Add custom check
echo "🔍 Checking for hardcoded IPs..."
if git diff --cached | grep -E '[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}'; then
    echo "❌ Hardcoded IP addresses found"
    exit 1
fi

# Check for TODOs on main
if [[ $BRANCH == "main" ]]; then
    if git diff --cached | grep -i "TODO"; then
        echo "❌ TODO comments not allowed on main"
        exit 1
    fi
fi
```

### Q: Can hooks run tests?

**A:** Yes, but be careful:

```bash
# Fast unit tests: OK
./gradlew test  # 10-30 seconds

# Slow integration tests: Consider CI instead
./gradlew connectedCheck  # 5-10 minutes ❌

# Compromise: Run subset
./gradlew testDebugUnitTest -x lintDebug
```

### Q: How do I test hooks before committing them?

**A:** Use the test script:

```bash
# Test all hooks
./test-hooks.sh all

# Test specific hook
./test-hooks.sh commit-msg
./test-hooks.sh pre-commit
./test-hooks.sh pre-push
```

---

## 🆘 Still Having Issues?

1. **Check documentation**: [GIT_HOOKS.md](GIT_HOOKS.md)
2. **Run debug mode**: `bash -x .git/hooks/<hook-name>`
3. **Check examples**: [HOOKS_EXAMPLES.md](HOOKS_EXAMPLES.md)
4. **Ask team**: Slack/Teams/Discord
5. **Create issue**: In project repository

**Emergency bypass** (document reason!):

```bash
git commit --no-verify -m "type: description [BYPASS: reason]"
```

---

**Remember**: Hooks are helpers, not enemies! They catch issues early and keep code quality high. 🚀
