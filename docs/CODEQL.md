# CodeQL Security Analysis

This project uses [GitHub CodeQL](https://codeql.github.com/) for automated security vulnerability scanning.

## 📋 What is CodeQL?

CodeQL is GitHub's semantic code analysis engine that:

- 🔍 Scans code for security vulnerabilities
- 🐛 Detects common coding errors
- 🛡️ Identifies potential exploits
- 📊 Provides detailed security reports
- 🤖 Runs automatically on every PR and push

## 🚀 How It Works

### Automatic Scans

CodeQL runs automatically:

1. **On every push** to `main`, `develop`, `release/**`
2. **On every pull request** to protected branches
3. **Weekly schedule** - Every Monday at 2 AM
4. **Manual trigger** - Via GitHub Actions UI

### What Gets Scanned

- ✅ All Kotlin code
- ✅ All Java code
- ✅ Security-related APIs
- ✅ Third-party library usage
- ❌ Build files (excluded)
- ❌ Test files (excluded)
- ❌ Generated code (excluded)

## 🔐 Security Checks

CodeQL checks for:

### High Severity

- SQL Injection
- Command Injection
- Path Traversal
- Insecure deserialization
- Hardcoded credentials
- Weak cryptography

### Medium Severity

- Information exposure
- Missing authentication
- Insecure data storage
- Unvalidated redirects
- Missing input validation

### Low Severity

- Code quality issues
- Resource leaks
- Null pointer dereferences
- Unused code

## 📊 Viewing Results

### In Pull Requests

When CodeQL finds issues in a PR:

1. PR checks show ❌ "CodeQL / Analyze (java-kotlin)"
2. Bot comments with summary
3. Click "Details" to see findings
4. **PR cannot merge** until issues resolved

### In Security Tab

1. Go to **Security** → **Code scanning**
2. Filter by:
    - Branch
    - Severity
    - State (open/closed)
    - Tool (CodeQL)
3. Click on alert for details
4. See:
    - Vulnerability description
    - Affected code location
    - Remediation advice
    - Related CVEs

### Example Alert

```
Alert: Insecure Android WebView
Severity: High
File: src/main/kotlin/WebViewActivity.kt
Line: 45

Description:
WebView has JavaScript enabled without proper security controls.

Recommendation:
1. Disable JavaScript if not needed
2. Implement WebViewClient with SSL validation
3. Use Content Security Policy
4. Sanitize loaded URLs

References:
- CWE-79: Cross-site Scripting
- OWASP Mobile Top 10: M1
```

## 🛠️ Configuration

### Basic Setup

Already configured! CodeQL runs automatically with:

- **Query suite**: `security-extended`
- **Languages**: Java, Kotlin
- **Schedule**: Weekly on Monday

### Custom Configuration

Edit `.github/codeql/codeql-config.yml`:

```yaml
# Use stricter query suite
queries:
  - uses: security-and-quality

# Exclude specific paths
paths-ignore:
  - '**/test/**'
  - '**/debug/**'

# Set minimum severity
min-severity: warning  # error, warning, recommendation, note
```

### Disable Specific Queries

If a query produces too many false positives:

```yaml
query-filters:
  - exclude:
      id: java/unused-local-variable
  - exclude:
      problem.severity: recommendation
```

## 🔧 Local Analysis (Optional)

### Install CodeQL CLI

```bash
# macOS
brew install codeql

# Linux
wget https://github.com/github/codeql-cli-binaries/releases/latest/download/codeql-linux64.zip
unzip codeql-linux64.zip
export PATH=$PATH:$PWD/codeql
```

### Run Locally

```bash
# 1. Create database
codeql database create codeql-db \
  --language=java \
  --command='./gradlew clean assembleDebug'

# 2. Run analysis
codeql database analyze codeql-db \
  --format=sarif-latest \
  --output=results.sarif \
  codeql/java-queries:codeql-suites/java-security-extended.qls

# 3. View results
codeql bqrs decode results.sarif
```

## 🚨 Handling Security Findings

### Priority Response Times

| Severity | Response Time | Max Resolution Time |
|----------|---------------|---------------------|
| Critical | Immediate     | 24 hours            |
| High     | 1 day         | 1 week              |
| Medium   | 1 week        | 1 month             |
| Low      | 1 month       | Next release        |

### Workflow

1. **Alert Created**
   ```
   CodeQL finds issue → Alert created → Team notified
   ```

2. **Triage**
   ```
   Review alert → Confirm it's real → Assign to developer
   ```

3. **Fix**
   ```
   Create branch → Fix issue → Add test → Create PR
   ```

4. **Verify**
   ```
   CodeQL rescans → Alert closes automatically → PR merges
   ```

### Dismissing False Positives

If an alert is a false positive:

1. Go to the alert in Security tab
2. Click **Dismiss alert**
3. Select reason:
    - False positive
    - Won't fix
    - Used in tests
4. Add comment explaining why
5. Submit

**Example dismissal comment:**

```
This WebView is only used for trusted, internal content loaded
from assets/. JavaScript is required for the interactive tutorial.
All content is validated and sanitized before loading.
```

## 📝 Best Practices

### DO ✅

1. **Fix critical/high severity issues** before merging
2. **Review all security alerts** within 48 hours
3. **Add comments** when dismissing alerts
4. **Test fixes** with specific test cases
5. **Update dependencies** with security patches
6. **Enable branch protection** requiring CodeQL pass

### DON'T ❌

1. Don't ignore security alerts
2. Don't dismiss without investigation
3. Don't disable CodeQL to "fix" CI
4. Don't commit sensitive data (CodeQL will find it!)
5. Don't use deprecated security APIs

## 🎯 Common Issues and Fixes

### 1. Hardcoded Credentials

❌ **Bad:**

```kotlin
val apiKey = "sk_live_1234567890abcdef"
val password = "admin123"
```

✅ **Good:**

```kotlin
val apiKey = BuildConfig.API_KEY
val password = EncryptedSharedPreferences.getString("password")
```

### 2. SQL Injection

❌ **Bad:**

```kotlin
val query = "SELECT * FROM users WHERE id = $userId"
database.rawQuery(query, null)
```

✅ **Good:**

```kotlin
val query = "SELECT * FROM users WHERE id = ?"
database.rawQuery(query, arrayOf(userId))
```

### 3. Insecure WebView

❌ **Bad:**

```kotlin
webView.settings.javaScriptEnabled = true
webView.loadUrl(untrustedUrl)
```

✅ **Good:**

```kotlin
webView.settings.apply {
    javaScriptEnabled = false  // Only if needed
    allowFileAccess = false
    allowContentAccess = false
}
webView.webViewClient = SecureWebViewClient()
webView.loadUrl(sanitizeUrl(trustedUrl))
```

### 4. Weak Cryptography

❌ **Bad:**

```kotlin
val cipher = Cipher.getInstance("DES")  // Weak
val md5 = MessageDigest.getInstance("MD5")  // Broken
```

✅ **Good:**

```kotlin
val cipher = Cipher.getInstance("AES/GCM/NoPadding")
val hash = MessageDigest.getInstance("SHA-256")
```

### 5. Insecure Random

❌ **Bad:**

```kotlin
val random = Random()
val token = random.nextInt()
```

✅ **Good:**

```kotlin
val random = SecureRandom()
val token = ByteArray(32)
random.nextBytes(token)
```

## 📊 Metrics and Reporting

### Weekly Security Report

Every Monday, CodeQL:

1. Scans entire codebase
2. Generates SARIF report
3. Updates Security dashboard
4. Creates issues for new findings

### KPIs to Track

- **Mean Time to Remediate (MTTR)**: Average time to fix alerts
- **Open Alerts**: Current unresolved security issues
- **Dismissed Alerts**: False positives or accepted risks
- **Fixed Alerts**: Resolved security issues

### Dashboard

View metrics at:

- **Insights** → **Security** → **Code scanning**
- Filter by time period, severity, state

## 🔗 Integration with Other Tools

### Branch Protection

Require CodeQL to pass before merging:

```yaml
# .github/branch-protection.yml
branches:
  main:
    protection:
      required_status_checks:
        strict: true
        contexts:
          - "CodeQL / Analyze (java-kotlin)"
```

### Dependabot

CodeQL works with Dependabot:

- Dependabot updates dependencies
- CodeQL scans for new vulnerabilities
- Alerts created if update introduces issues

### Pull Request Comments

CodeQL automatically comments on PRs with:

- Summary of findings
- Link to detailed report
- Recommended actions

## 🆘 Troubleshooting

### CodeQL fails to build

**Problem**: Build fails during CodeQL analysis

**Solution**:

```yaml
# Add build configuration to workflow
- name: Build project
  run: |
    ./gradlew assembleDebug \
      --no-daemon \
      --stacktrace \
      -x lint \
      -x test
```

### Too many false positives

**Problem**: CodeQL reports issues that aren't real vulnerabilities

**Solution**:

1. Review query documentation
2. Add exclusions in config
3. Dismiss with detailed comments
4. Consider custom queries

### Analysis takes too long

**Problem**: CodeQL times out (>30 minutes)

**Solution**:

```yaml
# Increase timeout
timeout-minutes: 60

# Or exclude large directories
paths-ignore:
  - '**/generated/**'
```

## 📚 Resources

- [CodeQL Documentation](https://codeql.github.com/docs/)
- [Java/Kotlin Queries](https://codeql.github.com/codeql-query-help/java/)
- [OWASP Mobile Top 10](https://owasp.org/www-project-mobile-top-10/)
- [CWE Database](https://cwe.mitre.org/)

## 🤝 Support

**Questions about an alert?**

1. Read the alert description
2. Check CodeQL documentation
3. Ask in #security-team Slack
4. Create issue with `security` label

**Need to override an alert?**

1. Document why it's safe
2. Get security team approval
3. Dismiss with detailed comment

---

**Last updated**: 2024-10-11
**Maintainer**: Security Team
**Contact**: security@company.com
