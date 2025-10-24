# 📚 Cache with expiration

## 📖 Table of Contents

### 🚀 Getting Started

- **[Quick Start Guide](../README.md#getting-started)** - Get up and running in 5 minutes
- **[Contributing Guide](../CONTRIBUTING.md)** - How to contribute to the project

### 🔒 Security

- **[Security Policy](SECURITY.md)** - How to report vulnerabilities
- **[CodeQL Security](CODEQL.md)** - Automated security scanning guide
- **[CodeQL Quick Start](CODEQL_QUICKSTART.md)** - 5-minute CodeQL setup

### 🪝 Git Hooks

- **[Git Hooks Guide](GIT_HOOKS.md)** - Complete hooks documentation
- **[Hooks Examples](HOOKS_EXAMPLES.md)** - Real-world usage examples
- **[Git Hooks Cheat Sheet](GIT_HOOKS_CHEATSHEET.md)** - Quick reference
- **[Git Hooks FAQ](GIT_HOOKS_FAQ.md)** - Common questions and answers

### 🤖 Automation

- **[Dependabot Configuration](DEPENDABOT.md)** - Dependency management
- **[CI/CD Workflows](../.github/workflows/)** - GitHub Actions pipelines

## 📋 Documentation by Topic

### For New Contributors

Start here if you're new to the project:

1. Read the [README](../README.md)
2. Review [Contributing Guidelines](../CONTRIBUTING.md)
3. Set up [Git Hooks](GIT_HOOKS.md)
4. Understand [Commit Conventions](GIT_HOOKS_CHEATSHEET.md)

### For Developers

Essential reading for active development:

- [Git Hooks Guide](GIT_HOOKS.md) - Pre-commit checks and validation
- [Hooks Examples](HOOKS_EXAMPLES.md) - How to use hooks effectively
- [Git Hooks FAQ](GIT_HOOKS_FAQ.md) - Troubleshooting common issues

### For Maintainers

Security and maintenance documentation:

- [Security Policy](SECURITY.md) - Vulnerability disclosure process
- [CodeQL Guide](CODEQL.md) - Security scanning and alerts
- [Dependabot](DEPENDABOT.md) - Automated dependency updates

### For Security Team

- [Security Policy](SECURITY.md) - Incident response procedures
- [CodeQL Documentation](CODEQL.md) - Security analysis and reporting
- [CodeQL Quick Start](CODEQL_QUICKSTART.md) - Emergency setup

## 🛠️ Scripts Documentation

Scripts are located in the `../scripts/` directory:

### Available Scripts

| Script                      | Purpose            | Usage                          |
|-----------------------------|--------------------|--------------------------------|
| `setup-git-hooks.sh`        | Install Git hooks  | `./scripts/setup-git-hooks.sh` |
| `test-hooks.sh`             | Test hooks         | `./scripts/test-hooks.sh all`  |
| `validate-hooks.sh`         | Validate syntax    | `./scripts/validate-hooks.sh`  |
| `advanced-version-check.sh` | Version validation | Used by pre-push hook          |

## 📊 Quick Links

### Workflows

- [Main Build](../.github/workflows/main.yml) - Production builds
- [Pull Request](../.github/workflows/pull-request.yml) - PR validation
- [CodeQL](../.github/workflows/codeql.yml) - Security scanning
- [Dependabot](../.github/workflows/dependabot-auto-merge.yml) - Auto-merge

### Configuration

- [Dependabot Config](../.github/dependabot/dependabot.yml)
- [CodeQL Config](../.github/codeql/codeql-config.yml)
- [Git Hooks Config](../.githooks/config)

## 🎯 Common Tasks

### Setting Up Development Environment

```bash
# 1. Clone repository
git clone https://github.com/rkociniewski/gac.git

# 2. Install Git hooks
./scripts/setup-git-hooks.sh

# 3. Build project
./gradlew build

# 4. Run tests
./gradlew test
```

### Making Your First Contribution

```bash
# 1. Create feature branch
git checkout -b feature/my-feature

# 2. Make changes
# ... edit files ...

# 3. Commit (hooks validate automatically)
git commit -m "feat: Add my feature"

# 4. Push and create PR
git push origin feature/my-feature
```

### Fixing Security Issues

```bash
# 1. Check CodeQL alerts
# Go to Security → Code scanning

# 2. Fix issue in code
# ... make changes ...

# 3. Commit with reference
git commit -m "security: Fix SQL injection (fixes #123)"

# 4. Verify fix
# CodeQL will rescan automatically
```

## 📚 External Resources

- [Kotlin Documentation](https://kotlinlang.org/docs/)
- [Android Developers](https://developer.android.com/)
- [Jetpack Compose](https://developer.android.com/jetpack/compose)
- [Conventional Commits](https://www.conventionalcommits.org/)
- [Git Flow](https://nvie.com/posts/a-successful-git-branching-model/)
- [OWASP Mobile Top 10](https://owasp.org/www-project-mobile-top-10/)

## 🆘 Need Help?

- **Questions**: [GitHub Discussions](https://github.com/rkociniewski/gac/discussions)
- **Bug Reports**: [GitHub Issues](https://github.com/rkociniewski/gac/issues)
- **Security Issues**: See [Security Policy](SECURITY.md)
- **General**: Check the [FAQ](GIT_HOOKS_FAQ.md)

## 📝 Contributing to Documentation

Found an issue in the documentation? Want to improve it?

1. Documentation is written in Markdown
2. Follow the existing structure
3. Keep it clear and concise
4. Include examples where helpful
5. Submit a PR with your changes

```bash
git checkout -b docs/improve-readme
# Edit documentation
git commit -m "docs: Improve Git Hooks guide"
git push origin docs/improve-readme
```

## 🔄 Keeping Documentation Updated

Documentation should be updated when:

- ✅ Adding new features
- ✅ Changing workflows or processes
- ✅ Fixing bugs that affect documented behavior
- ✅ Adding new scripts or tools
- ✅ Updating dependencies with breaking changes

## 📋 Documentation Checklist

When adding new features, ensure:

- [ ] README.md updated
- [ ] Relevant guide updated (hooks, security, etc.)
- [ ] Examples added if needed
- [ ] FAQ updated for common questions
- [ ] Scripts documented
- [ ] Changelog updated

---

**Last Updated**: 2024-10-11
**Maintained By**: [@rkociniewski](https://github.com/rkociniewski)

For the latest documentation, always check the [GitHub repository](https://github.com/rkociniewski/gac).
