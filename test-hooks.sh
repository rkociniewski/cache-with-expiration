#!/bin/bash
# Test Git hooks without actually committing
# Usage: ./test-hooks.sh [hook-name]

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

HOOKS_DIR=".githooks"
TEST_RESULTS=()

# Function to test commit-msg hook
test_commit_msg() {
    echo -e "${BLUE}Testing commit-msg hook...${NC}"
    echo ""

    local test_messages=(
        "feat: Add new feature|✅|valid"
        "fix: Resolve bug|✅|valid"
        "ci: Update workflows|✅|valid"
        "feat(auth): Add login|✅|valid with scope"
        "Added new feature|❌|missing type"
        "feat:Missing space|❌|no space after colon"
        "invalid: Wrong type|❌|invalid type"
        "fix: a|❌|too short"
        "Merge branch 'feature'|✅|merge commit"
        "Revert \"feat: Add feature\"|✅|revert commit"
    )

    local passed=0
    local failed=0

    for test_case in "${test_messages[@]}"; do
        IFS='|' read -r message expected description <<< "$test_case"

        # Create temporary file with commit message
        TEMP_MSG=$(mktemp)
        echo "$message" > "$TEMP_MSG"

        # Run hook
        if bash "$HOOKS_DIR/commit-msg" "$TEMP_MSG" >/dev/null 2>&1; then
            result="✅"
        else
            result="❌"
        fi

        # Check if result matches expected
        if [ "$result" == "$expected" ]; then
            echo -e "${GREEN}PASS${NC}: $description - \"$message\""
            ((passed++))
        else
            echo -e "${RED}FAIL${NC}: $description - \"$message\" (expected $expected, got $result)"
            ((failed++))
        fi

        rm "$TEMP_MSG"
    done

    echo ""
    echo "Results: $passed passed, $failed failed"
    TEST_RESULTS+=("commit-msg: $passed passed, $failed failed")

    return $failed
}

# Function to test pre-commit hook
test_pre_commit() {
    echo -e "${BLUE}Testing pre-commit hook...${NC}"
    echo ""

    # Save current state
    git stash push -u -m "test-hooks-backup" >/dev/null 2>&1 || true

    local passed=0
    local failed=0

    # Test 1: Debug statements
    echo -e "${YELLOW}Test 1: Debug statements detection${NC}"
    echo 'System.out.println("debug")' > TestFile.kt
    git add TestFile.kt

    if bash "$HOOKS_DIR/pre-commit" 2>&1 | grep -q "Debug statements"; then
        echo -e "${GREEN}PASS${NC}: Debug statements detected"
        ((passed++))
    else
        echo -e "${RED}FAIL${NC}: Debug statements not detected"
        ((failed++))
    fi

    git reset HEAD TestFile.kt >/dev/null 2>&1
    rm -f TestFile.kt

    # Test 2: Large file
    echo -e "${YELLOW}Test 2: Large file detection${NC}"
    dd if=/dev/zero of=large_file.bin bs=1M count=6 2>/dev/null
    git add large_file.bin

    if ! bash "$HOOKS_DIR/pre-commit" >/dev/null 2>&1; then
        echo -e "${GREEN}PASS${NC}: Large file blocked"
        ((passed++))
    else
        echo -e "${RED}FAIL${NC}: Large file not blocked"
        ((failed++))
    fi

    git reset HEAD large_file.bin >/dev/null 2>&1
    rm -f large_file.bin

    # Test 3: Secrets detection
    echo -e "${YELLOW}Test 3: Secrets detection${NC}"
    echo 'val API_KEY = "sk_live_1234567890abcdef"' > Secrets.kt
    git add Secrets.kt

    if ! bash "$HOOKS_DIR/pre-commit" >/dev/null 2>&1; then
        echo -e "${GREEN}PASS${NC}: Secret detected and blocked"
        ((passed++))
    else
        echo -e "${RED}FAIL${NC}: Secret not detected"
        ((failed++))
    fi

    git reset HEAD Secrets.kt >/dev/null 2>&1
    rm -f Secrets.kt

    # Test 4: Merge conflicts
    echo -e "${YELLOW}Test 4: Merge conflict detection${NC}"
    cat > Conflict.kt << 'EOF'
fun test() {
<<<<<<< HEAD
    println("version 1")
=======
    println("version 2")
>>>>>>> feature
}
EOF
    git add Conflict.kt

    if ! bash "$HOOKS_DIR/pre-commit" >/dev/null 2>&1; then
        echo -e "${GREEN}PASS${NC}: Merge conflict detected"
        ((passed++))
    else
        echo -e "${RED}FAIL${NC}: Merge conflict not detected"
        ((failed++))
    fi

    git reset HEAD Conflict.kt >/dev/null 2>&1
    rm -f Conflict.kt

    # Restore state
    git stash pop >/dev/null 2>&1 || true

    echo ""
    echo "Results: $passed passed, $failed failed"
    TEST_RESULTS+=("pre-commit: $passed passed, $failed failed")

    return $failed
}

# Function to test pre-push hook
test_pre_push() {
    echo -e "${BLUE}Testing pre-push hook (version check)...${NC}"
    echo ""

    local passed=0
    local failed=0

    # Test: Version extraction
    echo -e "${YELLOW}Test: Version extraction${NC}"

    if [ -f "build.gradle.kts" ]; then
        BUILD_FILE="build.gradle.kts"
    fi

    VERSION_NAME=$(grep -E 'version\s*=\s*["\047][^"\047]+["\047]' "$BUILD_FILE" | grep -oE '["\047][^"\047]+["\047]' | tr -d '"' | tr -d "'" | head -1)

    if [ -n "$VERSION_NAME" ]; then
        echo -e "${GREEN}PASS${NC}: version=$VERSION_NAME"
        ((passed++))
    else
        echo -e "${RED}FAIL${NC}: Could not extract version info"
        ((failed++))
    fi

    # Test: Semantic versioning validation
    echo -e "${YELLOW}Test: Semantic versioning validation${NC}"

    if [[ $VERSION_NAME =~ ^[0-9]+\.[0-9]+\.[0-9]+ ]]; then
        echo -e "${GREEN}PASS${NC}: Valid semantic version: $VERSION_NAME"
        ((passed++))
    else
        echo -e "${RED}FAIL${NC}: Invalid semantic version: $VERSION_NAME"
        ((failed++))
    fi

    echo ""
    echo "Results: $passed passed, $failed failed"
    TEST_RESULTS+=("pre-push: $passed passed, $failed failed")

    return $failed
}

# Function to run all tests
test_all() {
    local total_failed=0

    echo -e "${BLUE}╔════════════════════════════════════════╗${NC}"
    echo -e "${BLUE}║     Git Hooks Test Suite              ║${NC}"
    echo -e "${BLUE}╚════════════════════════════════════════╝${NC}"
    echo ""

    # Check if hooks exist
    if [ ! -d "$HOOKS_DIR" ]; then
        echo -e "${RED}Error: $HOOKS_DIR directory not found${NC}"
        echo "Run ./setup-git-hooks.sh first"
        exit 1
    fi

    # Test each hook
    if [ -f "$HOOKS_DIR/commit-msg" ]; then
        test_commit_msg
        ((total_failed+=$?))
        echo ""
    fi

    if [ -f "$HOOKS_DIR/pre-commit" ]; then
        test_pre_commit
        ((total_failed+=$?))
        echo ""
    fi

    if [ -f "$HOOKS_DIR/pre-push" ]; then
        test_pre_push
        ((total_failed+=$?))
        echo ""
    fi

    # Summary
    echo -e "${BLUE}╔════════════════════════════════════════╗${NC}"
    echo -e "${BLUE}║     Test Summary                       ║${NC}"
    echo -e "${BLUE}╚════════════════════════════════════════╝${NC}"
    echo ""

    for result in "${TEST_RESULTS[@]}"; do
        echo "  $result"
    done

    echo ""

    if [ $total_failed -eq 0 ]; then
        echo -e "${GREEN}✅ All tests passed!${NC}"
        return 0
    else
        echo -e "${RED}❌ Some tests failed${NC}"
        return 1
    fi
}

# Main
HOOK_NAME=${1:-all}

case $HOOK_NAME in
    commit-msg)
        test_commit_msg
        ;;
    pre-commit)
        test_pre_commit
        ;;
    pre-push)
        test_pre_push
        ;;
    all)
        test_all
        ;;
    *)
        echo "Usage: $0 [commit-msg|pre-commit|pre-push|all]"
        exit 1
        ;;
esac
