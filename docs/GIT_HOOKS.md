# Git Hooks Documentation

This project uses Git hooks to automatically validate code before commits and pushes.

## 🚀 Quick Start

### Automatic Installation (Recommended)

Hooks are automatically installed during the first project build.

```bash
./gradlew build
```

### Manual Installation

```bash
chmod +x setup-git-hooks.sh
./setup-git-hooks.sh
```

## 📋 Installed Hooks

### 1. `commit-msg` - Commit Message Validation

Validates that commit messages follow [Conventional Commits](https://www.conventionalcommits.org/).

**Format:**

```
<type>[optional scope]: <description>

[optional body]

[optional footer]
```

**Examples:**

```bash
✅ feat: Add user authentication
✅ fix(ui): Correct button alignment
✅ ci: Update GitHub Actions workflows
✅ docs: Update README
❌ Added new feature  # Missing type
❌ feat:Add feature   # Missing space after :
```

**Allowed types:**

- `feat` - New feature
- `fix` - Bug fix
- `docs` - Documentation changes
- `style` - Code formatting
- `refactor` - Code refactoring
- `perf` - Performance optimization
- `test` - Adding/modifying tests
- `build` - Build system changes
- `ci` - CI/CD changes
- `chore` - Other tasks (dependencies, etc.)
- `revert` - Reverting changes

### 2. `pre-commit` - Pre-commit Checks

**Checks for:**

- 🐛 Debug statements (`println`, `Log.d`, `TODO`, `FIXME`)
    - **Excluded**: Logger classes (AppLogger, Logger, Log.kt), test files
- 📦 Large files (>5MB)
- 🔐 Potential secrets (API keys, passwords, tokens)
    - **Excluded**: Documentation files (*.md, *.txt), test files
- 🔀 Merge conflict markers
- 🎨 Code style (ktlint, if installed)
- 🔍 Code issues (detekt, if configured)

**Example output:**

```
🔍 Running pre-commit checks...
📝 Checking for debug statements...
📦 Checking for large files...
🔐 Checking for secrets...
🔀 Checking for merge conflicts...
✅ All pre-commit checks passed!
```

### 3. `prepare-commit-msg` - Message Preparation

Automatically adds issue numbers to commit messages if the branch contains an ID:

```bash
# Branch: feature/PROJ-123-user-login
# Commit: "feat: Add login screen"
# Result: "[PROJ-123] feat: Add login screen"
```

### 4. `post-commit` - Version Warning

After committing to `main`/`release/*`, reminds about version updates:

```
⚠️  You committed to main
❌ WARNING: Build file was NOT modified
   Did you remember to bump version?

   Current version info:
   version = "1.2.3"
```

### 5. `pre-push` - Version Bump Enforcement

Before pushing to `main`/`release/*`, verifies that the version was incremented:

```
🔍 Checking version bump for main...
Remote version: 1.2.3 (42)
Local version:  1.2.4 (43)
✅ Version check passed
```

**Error if version wasn't incremented:**

```
❌ ERROR: versionCode was not incremented!
   Remote: 42
   Local:  42

Please bump versionCode in build.gradle.kts
```

## 🛠️ Configuration

### Disable Specific Checks

In `pre-commit`, you can comment out unnecessary checks:

```bash
# Disable debug statement checking
# if git diff --cached --name-only | grep -E '\.(kt|java)$' | xargs grep -nE '(System\.out\.println|Log\.[dev])' 2>/dev/null; then
#     echo "Warning: Debug statements found"
# fi
```

### Add Custom Checks

Add new checks in `.githooks/pre-commit`:

```bash
# 7. Check for hardcoded strings
echo "🌍 Checking for hardcoded strings..."
if git diff --cached --name-only | grep -E '\.kt$' | xargs grep -nE 'text\s*=\s*"[^"]{10,}"' 2>/dev/null; then
    echo "⚠️  Hardcoded strings found - consider using string resources"
fi
```

## 🚫 Bypassing Hooks

### Bypass all hooks (use with caution!)

```bash
git commit --no-verify -m "Emergency fix"
git push --no-verify
```

### Bypass only pre-commit

```bash
SKIP_PRE_COMMIT=1 git commit -m "feat: Add feature"
```

Modify `.githooks/pre-commit`:

```bash
if [ -n "$SKIP_PRE_COMMIT" ]; then
    echo "⏭️  Skipping pre-commit checks"
    exit 0
fi
```

## 🔧 Troubleshooting

### Hook not working

```bash
# Check if hook is executable
ls -la .git/hooks/
chmod +x .git/hooks/*

# Or reinstall
./setup-git-hooks.sh
```

### Hook fails on Windows

On Windows, use Git Bash or:

```bash
# Copy instead of symlinking
cp .githooks/* .git/hooks/
```

### Want to see what the hook does

```bash
# Debug mode
bash -x .git/hooks/pre-commit
```

### Hook blocks important commit

```bash
# Emergency bypass (document why!)
git commit --no-verify -m "hotfix: Critical production fix"
```

## 📦 Updating Hooks

When hooks are updated in the repo:

```bash
git pull
./setup-git-hooks.sh  # Or: ./gradlew installGitHooks
```

## 🎓 Best Practices

### DO ✅

- Commit often with small changes
- Use clear, descriptive commit messages
- Bump version before merging to main
- Run tests locally before pushing
- Review what you're committing (`git diff --cached`)

### DON'T ❌

- Don't commit secrets/API keys
- Don't commit large binary files
- Don't use `--no-verify` without reason
- Don't commit untested code to main
- Don't forget to bump version

## 🤝 Team Collaboration

### New Team Member

```bash
git clone <repo>
cd <project>
./gradlew build  # Automatically installs hooks
```

### Updating Hooks in Project

```bash
# 1. Modify .githooks/*
# 2. Commit
git add .githooks/
git commit -m "ci: Update pre-commit hooks"

# 3. Team updates
git pull
./setup-git-hooks.sh
```

## 📚 Additional Resources

- [Conventional Commits](https://www.conventionalcommits.org/)
- [Git Hooks Documentation](https://git-scm.com/docs/githooks)
- [Semantic Versioning](https://semver.org/)

## 🆘 Help

Problems? Questions?

1. Check this document
2. Run: `bash -x .git/hooks/<hook-name>`
3. Ask on Slack/Teams
4. Create an issue in the repo
