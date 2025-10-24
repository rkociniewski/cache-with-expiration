#!/bin/bash
# Script to set up Git hooks for project
# Run: chmod +x setup-git-hooks.sh && ./setup-git-hooks.sh

set -e

HOOKS_DIR=".git/hooks"
SCRIPTS_DIR=".githooks"

echo "🔧 Setting up Git hooks..."

# Create scripts directory if it doesn't exist
mkdir -p "$SCRIPTS_DIR"

# Create commit-msg hook
cat > "$SCRIPTS_DIR/commit-msg" << 'EOF'
#!/bin/bash
# Validates commit message format (Conventional Commits)

commit_msg=$(cat "$1")
pattern="^(feat|fix|docs|style|refactor|perf|test|build|ci|chore|revert|update|add|remove|improve)(\(.+\))?: .{1,}"

# Allow merge commits
if echo "$commit_msg" | grep -qE "^Merge"; then
    exit 0
fi

# Allow revert commits
if echo "$commit_msg" | grep -qE "^Revert"; then
    exit 0
fi

if ! echo "$commit_msg" | grep -qE "$pattern"; then
    echo ""
    echo "❌ Invalid commit message format!"
    echo ""
    echo "Your commit message should follow Conventional Commits:"
    echo "  <type>[optional scope]: <description>"
    echo ""
    echo "Types: feat, fix, docs, style, refactor, perf, test, build, ci, chore, revert, update, add, remove, improve"
    echo ""
    echo "Examples:"
    echo "  ✅ feat: Add user authentication"
    echo "  ✅ fix(ui): Correct button alignment"
    echo "  ✅ ci: Update GitHub Actions workflows"
    echo ""
    exit 1
fi

exit 0
EOF

# Create pre-commit hook
cat > "$SCRIPTS_DIR/pre-commit" << 'PRECOMMIT_EOF'
#!/bin/bash
# Pre-commit checks for project

set -e

echo "🔍 Running pre-commit checks..."

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

# 1. Check for debug statements (exclude logger classes and test files)
echo "📝 Checking for debug statements..."
STAGED_FILES=$(git diff --cached --name-only --diff-filter=ACM | grep -E '\.(kt|java)$' | grep -v -E '(AppLogger|Logger|Log\.kt|Test\.kt|test/)' || true)

if [ -n "$STAGED_FILES" ]; then
    if echo "$STAGED_FILES" | xargs grep -nE '(System\.out\.println|Log\.[dev]|console\.log|debugger|TODO|FIXME)' 2>/dev/null; then
        echo -e "${YELLOW}⚠️  Warning: Debug statements found${NC}"
        echo "Consider removing them before committing"
        echo "Note: Logger/AppLogger classes are excluded from this check"
    fi
fi

# 2. Check for large files
echo "📦 Checking for large files..."
MAX_SIZE=5242880
for file in $(git diff --cached --name-only --diff-filter=ACM); do
    if [ -f "$file" ]; then
        size=$(wc -c < "$file" 2>/dev/null || echo 0)
        if [ "$size" -gt "$MAX_SIZE" ]; then
            echo -e "${RED}❌ File too large: $file ($(($size / 1048576))MB)${NC}"
            echo "Files larger than 5MB should not be committed"
            exit 1
        fi
    fi
done

# 3. Check for secrets/API keys (exclude documentation files)
echo "🔐 Checking for secrets..."
STAGED_CODE_FILES=$(git diff --cached --name-only --diff-filter=ACM | grep -v -E '\.(md|txt)$' | grep -v -E '(test/|Test\.kt|spec\.)' || true)

if [ -n "$STAGED_CODE_FILES" ]; then
    SECRET_FOUND=false

    for pattern in 'api[_-]?key\s*=\s*["\047][^"\047]{10,}' 'secret\s*=\s*["\047][^"\047]{10,}' 'password\s*=\s*["\047][^"\047]{5,}' 'token\s*=\s*["\047][^"\047]{10,}' 'aws[_-]?access[_-]?key' 'private[_-]?key'; do
        if echo "$STAGED_CODE_FILES" | xargs grep -lE "$pattern" 2>/dev/null >/dev/null; then
            echo -e "${RED}❌ Potential secret detected!${NC}"
            echo "Pattern: $pattern"
            echo "Make sure you're not committing sensitive data"
            echo "Files excluded: *.md, *.txt, *test*, *Test.kt"
            SECRET_FOUND=true
            break
        fi
    done

    if [ "$SECRET_FOUND" = true ]; then
        exit 1
    fi
fi

# 4. Check for merge conflict markers
echo "🔀 Checking for merge conflicts..."
if git diff --cached | grep -E '^(<<<<<<<|=======|>>>>>>>)' >/dev/null 2>&1; then
    echo -e "${RED}❌ Merge conflict markers found${NC}"
    echo "Please resolve all conflicts before committing"
    exit 1
fi

# 5. Run ktlint (if available)
if command -v ktlint &> /dev/null; then
    echo "🎨 Running ktlint..."
    STAGED_KT=$(git diff --cached --name-only --diff-filter=ACM | grep -E '\.kt$' || true)
    if [ -n "$STAGED_KT" ]; then
        if ! echo "$STAGED_KT" | xargs ktlint --relative 2>/dev/null; then
            echo -e "${RED}❌ Kotlin code style issues found${NC}"
            echo "Run: ./gradlew ktlintFormat"
            exit 1
        fi
    fi
fi

echo -e "${GREEN}✅ All pre-commit checks passed!${NC}"
exit 0
PRECOMMIT_EOF

# Create prepare-commit-msg hook (for version check)
cat > "$SCRIPTS_DIR/prepare-commit-msg" << 'EOF'
#!/bin/bash
# Adds branch name to commit message and checks version bump

COMMIT_MSG_FILE=$1
COMMIT_SOURCE=$2

# Only run for normal commits (not merge, squash, etc.)
if [ -z "$COMMIT_SOURCE" ]; then
    # Get current branch
    BRANCH_NAME=$(git symbolic-ref --short HEAD 2>/dev/null)

    # Add branch name to commit message if it's a feature/fix/etc branch
    if [[ $BRANCH_NAME =~ ^(feature|fix|hotfix|bugfix)/ ]]; then
        ISSUE=$(echo "$BRANCH_NAME" | grep -oE '[A-Z]+-[0-9]+' | head -1)
        if [ -n "$ISSUE" ]; then
            # Prepend issue number if not already in message
            if ! grep -q "$ISSUE" "$COMMIT_MSG_FILE"; then
                sed -i.bak "1s/^/[$ISSUE] /" "$COMMIT_MSG_FILE"
                rm "${COMMIT_MSG_FILE}.bak"
            fi
        fi
    fi
fi
EOF

# Create post-commit hook (for version warning)
cat > "$SCRIPTS_DIR/post-commit" << 'EOF'
#!/bin/bash
# Post-commit hook to check version bump on main/release branches

BRANCH_NAME=$(git symbolic-ref --short HEAD 2>/dev/null)

# Only check on important branches
if [[ ! $BRANCH_NAME =~ ^(main|release/) ]]; then
    exit 0
fi

echo ""
echo "⚠️  You committed to $BRANCH_NAME"

# Check if version files were modified
if git diff HEAD~1 HEAD --name-only | grep -qE 'build.gradle(\.kts)?$'; then
    echo "✅ Build file was modified (version might be updated)"
else
    echo "❌ WARNING: Build file was NOT modified"
    echo "   Did you remember to bump version?"
    echo ""
    echo "   Current version info:"

    if [ -f "build.gradle.kts" ]; then
        grep -E 'version' build.gradle.kts | head -2
    fi
    echo ""
fi

exit 0
EOF

# Create pre-push hook with FIXED VERSION PARSING
cat > "$SCRIPTS_DIR/pre-push" << 'PREPUSH_EOF'
#!/bin/bash
# Pre-push hook to verify version bump on main/release branches

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Get the branch being pushed to
while read local_ref local_sha remote_ref remote_sha; do
    BRANCH_NAME=$(echo "$remote_ref" | sed 's/refs\/heads\///')

    # Only check main and release branches
    if [[ ! $BRANCH_NAME =~ ^(main|release/) ]]; then
        continue
    fi

    echo -e "${YELLOW}🔍 Checking version bump for $BRANCH_NAME...${NC}"

    # Get version info from current commit
    if [ -f "build.gradle.kts" ]; then
        BUILD_FILE="build.gradle.kts"
    else
        echo -e "${YELLOW}⚠️  No build.gradle.kts file found, skipping version check${NC}"
        continue
    fi
    # Try double quotes first
    CURRENT_VERSION_NAME=$(grep -E 'version\s*=\s*"[^"]+"' "$BUILD_FILE" | grep -oE '"[^"]+"' | tr -d '"' | head -1)

    # Fallback to single quotes if not found
    if [ -z "$CURRENT_VERSION_NAME" ]; then
        CURRENT_VERSION_NAME=$(grep -E "version\s*=\s*'[^']+'" "$BUILD_FILE" | grep -oE "'[^']+'" | tr -d "'" | head -1)
    fi

    # Get version from remote (if exists)
    if git rev-parse "origin/$BRANCH_NAME" >/dev/null 2>&1; then
        REMOTE_BUILD_CONTENT=$(git show "origin/$BRANCH_NAME:$BUILD_FILE" 2>/dev/null || echo "")

        if [ -n "$REMOTE_BUILD_CONTENT" ]; then

            # Try double quotes first
            REMOTE_VERSION_NAME=$(echo "$REMOTE_BUILD_CONTENT" | grep -E 'version\s*=\s*"[^"]+"' | grep -oE '"[^"]+"' | tr -d '"' | head -1)

            # Fallback to single quotes
            if [ -z "$REMOTE_VERSION_NAME" ]; then
                REMOTE_VERSION_NAME=$(echo "$REMOTE_BUILD_CONTENT" | grep -E "version\s*=\s*'[^']+'" | grep -oE "'[^']+'" | tr -d "'" | head -1)
            fi

            echo "Remote version: $REMOTE_VERSION_NAME"
            echo "Local version:  $CURRENT_VERSION_NAME"
            echo ""

            # Check version
            if [ "$CURRENT_VERSION_NAME" == "$REMOTE_VERSION_NAME" ]; then
                echo -e "${RED}❌ ERROR: version was not changed!${NC}"
                echo "   Version: $CURRENT_VERSION_NAME"
                echo ""
                echo "Please bump version in $BUILD_FILE"

                # Suggest next version based on current
                if [[ $CURRENT_VERSION_NAME =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)$ ]]; then
                    MAJOR="${BASH_REMATCH[1]}"
                    MINOR="${BASH_REMATCH[2]}"
                    PATCH="${BASH_REMATCH[3]}"
                    NEXT_PATCH=$((PATCH + 1))
                    echo "Suggested: $MAJOR.$MINOR.$NEXT_PATCH"
                fi
                exit 1
            fi

            echo -e "${GREEN}✅ version updated${NC}"
            echo "   $REMOTE_VERSION_NAME → $CURRENT_VERSION_NAME"
            echo ""
            echo -e "${GREEN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
            echo -e "${GREEN}✅ Version check passed${NC}"
            echo -e "${GREEN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
        else
            echo -e "${YELLOW}⚠️  Could not read remote build file${NC}"
        fi
    else
        echo -e "${YELLOW}⚠️  No remote branch found, skipping comparison${NC}"
    fi

    echo ""
done

exit 0
PREPUSH_EOF

# Make all hooks executable
chmod +x "$SCRIPTS_DIR"/*

# Link hooks to .git/hooks
for hook in commit-msg pre-commit prepare-commit-msg post-commit pre-push; do
    if [ -f "$SCRIPTS_DIR/$hook" ]; then
        ln -sf "../../$SCRIPTS_DIR/$hook" "$HOOKS_DIR/$hook"
        echo "✅ Installed $hook hook"
    fi
done

echo ""
echo "🎉 Git hooks installed successfully!"
echo ""
echo "Installed hooks:"
echo "  • commit-msg         - Validates commit message format"
echo "  • pre-commit         - Checks code quality, secrets, conflicts"
echo "  • prepare-commit-msg - Adds issue numbers to commits"
echo "  • post-commit        - Warns about version bumps"
echo "  • pre-push           - Enforces version bump on main/release"
echo ""
echo "To bypass hooks (use with caution):"
echo "  git commit --no-verify"
echo ""
echo "To uninstall:"
echo "  rm -rf .githooks .git/hooks/*"
echo ""
