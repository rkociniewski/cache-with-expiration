# CodeQL - Quick Start Guide

## ⚡ 5-Minute Setup

### Step 1: Enable CodeQL

CodeQL is **already configured** in `.github/workflows/codeql.yml`!

Just commit and push:

```bash
git add .github/workflows/codeql.yml
git commit -m "ci: Add CodeQL security scanning"
git push origin main
```

### Step 2: Enable Code Scanning (GitHub Settings)

1. Go to **Settings** → **Code security and analysis**
2. Enable **Code scanning**
    - Click **Set up** → **Default**
    - Or use existing workflow (already done!)
3. Enable **Secret scanning** (recommended)
4. Enable **Dependency review** (recommended)

### Step 3: Verify It Works

```bash
# Trigger manual run
# Go to Actions → CodeQL Security Analysis → Run workflow

# Or push to main
git push origin main

# Check results
# Go to Security → Code scanning
```

---

## 📊 First Scan Results

After first scan completes (~5-10 minutes):

### If NO Issues Found ✅

```
🎉 Congratulations! No security vulnerabilities detected.

Next steps:
- CodeQL will run automatically on every PR
- Weekly scans on Monday
- Review Security tab regularly
```

### If Issues Found ⚠️

```
⚠️ CodeQL found X security issues

Priority order:
1. Fix Critical (within 24h)
2. Fix High (within 1 week)
3. Review Medium (within 1 month)
4. Note Low (next release)

View details: Security → Code scanning
```

---

## 🚨 Common First-Time Findings

### 1. Hardcoded Secrets

**Alert**: "Hard-coded credentials"

**Quick Fix**:

```kotlin
// ❌ Before
val API_KEY = "sk_live_abc123"

// ✅ After
val API_KEY = BuildConfig.API_KEY
```

### 2. SQL Injection Risk

**Alert**: "SQL injection vulnerability"

**Quick Fix**:

```kotlin
// ❌ Before
db.rawQuery("SELECT * FROM users WHERE id = $id", null)

// ✅ After
db.rawQuery("SELECT * FROM users WHERE id = ?", arrayOf(id))
```

### 3. Insecure WebView

**Alert**: "WebView JavaScript enabled without security"

**Quick Fix**:

```kotlin
// ❌ Before
webView.settings.javaScriptEnabled = true

// ✅ After (if JS needed)
webView.settings.apply {
    javaScriptEnabled = true
    domStorageEnabled = false
    allowFileAccess = false
}
webView.webViewClient = SecureWebViewClient()
```

---

## 🔧 Configuration

### Basic (Already Done)

```yaml
# .github/workflows/codeql.yml
-   uses: github/codeql-action/init@v3
    with:
        languages: java-kotlin
        queries: security-extended
```

### Advanced (Optional)

Create `.github/codeql/codeql-config.yml`:

```yaml
name: "Custom CodeQL Config"

queries:
    -   uses: security-and-quality

paths-ignore:
    - '**/test/**'
    - '**/build/**'

min-severity: warning
```

Then update workflow:

```yaml
-   uses: github/codeql-action/init@v3
    with:
        config-file: .github/codeql/codeql-config.yml
```

---

## 📋 Daily Workflow

### As a Developer

**Before Creating PR**:

```bash
# 1. Write code
# 2. Commit (hooks will check)
git commit -m "feat: Add new feature"

# 3. Push
git push origin feature/my-feature

# 4. Create PR
# CodeQL runs automatically

# 5. If CodeQL finds issues:
#    - Click "Details" in PR checks
#    - Fix reported issues
#    - Push fixes
#    - CodeQL re-runs automatically
```

**Fixing Security Issues**:

```bash
# 1. Go to Security → Code scanning
# 2. Click on alert
# 3. Read description & recommendations
# 4. Fix in code
# 5. Commit with reference:
git commit -m "security: Fix SQL injection (fixes #123)"
```

### As a Reviewer

**Reviewing PRs**:

```
1. Check if CodeQL passed ✅
2. If failed ❌:
   - Review security findings
   - Ensure developer fixed issues
   - Don't merge until clean
3. Review dismissals (if any)
```

---

## 🎯 Quick Reference

### Severity Levels

| Severity    | Response Time   | Example Issues     |
|-------------|-----------------|--------------------|
| 🔴 Critical | Immediate (24h) | SQL Injection, RCE |
| 🟠 High     | 1 week          | XSS, Auth bypass   |
| 🟡 Medium   | 1 month         | Info disclosure    |
| 🟢 Low      | Next release    | Code quality       |

### Common Commands

```bash
# Trigger manual scan
gh workflow run codeql.yml

# View security alerts
gh api repos/:owner/:repo/code-scanning/alerts

# List open alerts
gh api repos/:owner/:repo/code-scanning/alerts \
  --jq '.[] | select(.state=="open") | {number, severity: .rule.severity, desc: .rule.description}'

# View specific alert
gh api repos/:owner/:repo/code-scanning/alerts/ALERT_NUMBER
```

---

## 🚫 Dismissing False Positives

### When to Dismiss

✅ **OK to dismiss**:

- Confirmed false positive
- Test code only
- Deprecated code being removed
- Risk accepted by security team

❌ **NOT OK to dismiss**:

- "Too hard to fix"
- "We'll fix it later"
- "It's not important"
- Without security review

### How to Dismiss

**Via UI**:

```
1. Security → Code scanning
2. Click alert
3. Dismiss alert
4. Select reason:
   - False positive
   - Won't fix
   - Used in tests
5. Add detailed comment
6. Submit
```

**Example Comment**:

```
This alert is a false positive because:
- The WebView only loads trusted local HTML from assets/
- JavaScript is required for the help documentation
- Content is validated and sanitized before loading
- External URLs are blocked via shouldOverrideUrlLoading()

Reviewed by: @security-team
Date: 2024-10-11
```

---

## 📊 Monitoring

### Weekly Reports

Every Monday at 9 AM:

- Automatic report generated
- Issue created/updated
- Lists all open alerts
- Prioritizes by severity

### Metrics to Track

```
1. Open Alerts: Current unresolved issues
2. MTTR: Mean time to remediate
3. New vs Fixed: Weekly trend
4. Dismissed: Accepted risks
```

### Dashboard

View at: **Security** → **Code scanning**

Filters:

- Branch
- Severity
- State (open/closed/dismissed)
- Tool (CodeQL)
- Time period

---

## 🔗 Integration with CI/CD

### Branch Protection

Require CodeQL to pass:

```yaml
# Settings → Branches → main
Branch protection rules:
    ✅ Require status checks to pass
    ✅ CodeQL / Analyze (java-kotlin)
```

### Pull Request Flow

```
1. Developer pushes code
   ↓
2. CodeQL scans automatically
   ↓
3. Results in PR checks
   ↓
4a. ✅ Passes → Can merge
4b. ❌ Fails → Must fix
```

### Auto-block on Issues

Add to `.github/workflows/codeql.yml`:

```yaml
-   name: Check for high severity issues
    if: github.event_name == 'pull_request'
    run: |
        ALERTS=$(gh api repos/${{ github.repository }}/code-scanning/alerts \
          --jq '[.[] | select(.state=="open" and .rule.severity=="error")] | length')

        if [ "$ALERTS" -gt 0 ]; then
          echo "❌ Found $ALERTS high/critical issues"
          exit 1
        fi
    env:
        GH_TOKEN: ${{ github.token }}
```

---

## 🛠️ Troubleshooting

### CodeQL Fails to Run

**Problem**: Workflow fails with build error

**Solution**:

```yaml
# Add to build step
-   name: Build project
    run: |
        ./gradlew assembleDebug \
          --no-daemon \
          -x lint \
          -x test
```

### CodeQL Times Out

**Problem**: Analysis takes too long and times out after 30+ minutes

**Symptom**: Workflow cancelled after loading many queries

**Solutions**:

**Option 1: Increase timeout**

```yaml
jobs:
    analyze:
        timeout-minutes: 60  # Zwiększ z 30 do 60
```

**Option 2: Use security-only queries** (RECOMMENDED)

```yaml
-   uses: github/codeql-action/init@v3
    with:
        queries: security-only  # Zamiast security-extended
```

**Option 3: Exclude more paths**

```yaml
# .github/codeql/codeql-config.yml
paths-ignore:
    - '**/build/**'
    - '**/test/**'
    - '**/res/**'  # Dodaj resources
    - '**/assets/**'

paths:
    - 'src/main/kotlin/**'  # Tylko główny kod
```

**Option 4: Use lite workflow**

```bash
# Użyj .github/workflows/codeql-lite.yml
# Szybsza wersja z minimalnymi queries
```

**Option 5: Allocate more resources**

```yaml
-   uses: github/codeql-action/analyze@v3
    with:
        threads: 4  # Więcej wątków
        ram: 6144   # Więcej RAM (6GB)
```

### No Results Shown

**Problem**: Scan completes but no results

**Solution**:

1. Check if code scanning is enabled
2. Verify permissions in workflow
3. Check excluded paths in config
4. Wait 5-10 minutes for processing

### Too Many Alerts

**Problem**: Overwhelmed by alerts

**Solution**:

```
Priority:
1. Fix all Critical/High first
2. Review Medium, dismiss false positives
3. Plan Low fixes for next sprint
4. Update config to reduce noise
```

---

## 📚 Resources

**Essential Reading**:

- [CODEQL.md](CODEQL.md) - Full documentation
- [CodeQL Queries](https://codeql.github.com/codeql-query-help/java/)

**Tools**:

- [CodeQL CLI](https://codeql.github.com/docs/codeql-cli/)
- [VS Code Extension](https://marketplace.visualstudio.com/items?itemName=GitHub.vscode-codeql)
- [GitHub CLI](https://cli.github.com/)

**Support**:

- Slack: #security-team
- Issues: Label with `security`
- Email: security@company.com

---

## ✅ Checklist

### Initial Setup

- [ ] CodeQL workflow added
- [ ] Code scanning enabled in Settings
- [ ] First scan completed
- [ ] Team notified about new tool
- [ ] Branch protection configured

### For Developers

- [ ] Read this guide
- [ ] Know where to view alerts
- [ ] Understand severity levels
- [ ] Know how to fix common issues
- [ ] Can dismiss false positives properly

### For Team Leads

- [ ] Weekly reports reviewed
- [ ] Critical issues tracked
- [ ] Security policy updated
- [ ] Team trained on CodeQL
- [ ] Metrics being monitored

---

## 🎓 Training

### For New Team Members

**30-minute onboarding**:

1. Read this guide (10 min)
2. View sample alerts (5 min)
3. Practice fixing test issue (10 min)
4. Review dismissal process (5 min)

**Practice Exercise**:

```kotlin
// Create a file with intentional issues
// Try to commit and see CodeQL catch them

// 1. SQL Injection
val query = "SELECT * FROM users WHERE name = '$userName'"

// 2. Hardcoded Secret
val apiKey = "sk_live_abc123def456"

// 3. Weak Crypto
val cipher = Cipher.getInstance("DES")

// Fix each one and watch CodeQL alerts clear
```

---

## 🚀 Next Steps

Now that CodeQL is set up:

1. **Week 1**: Fix all Critical/High issues
2. **Week 2**: Review and triage Medium issues
3. **Week 3**: Set up automated reports
4. **Week 4**: Train team on security best practices
5. **Ongoing**: Monitor weekly reports and maintain security

---

**Questions?** Check [CODEQL.md](CODEQL.md) or ask in #security-team

**Last updated**: 2024-10-11
