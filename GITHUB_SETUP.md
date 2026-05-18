# GitHub Setup Guide for ChatAgent

This guide walks you through creating and configuring your ChatAgent repository on GitHub.

## Step 1: Create GitHub Repository

### Option A: Via GitHub Web Interface (Recommended)

1. Go to [github.com/new](https://github.com/new)
2. **Repository name**: `chatagent`
3. **Description**: `AI-powered chat application with Spring Boot and PGVector`
4. **Visibility**: Choose based on preference:
   - **Public** - Open source project (recommended for portfolios)
   - **Private** - Company/personal use
5. **Initialize options**:
   - ✅ Add a README file (you already have one)
   - ✅ Add .gitignore (you already have one)
   - ✅ Choose a license: MIT (you already have one)
6. **Click**: "Create repository"

### Option B: Via GitHub CLI

```bash
# Install GitHub CLI if needed
brew install gh

# Login to GitHub
gh auth login

# Create repository
gh repo create chatagent \
  --source=. \
  --remote=origin \
  --push \
  --public \
  --description="AI-powered chat application with Spring Boot and PGVector"
```

## Step 2: Add Remote and Push Local Repository

If you created the repo via web interface:

```bash
cd /Users/yuqiguo/Desktop/chatagent

# Add GitHub as remote
git remote add origin https://github.com/yourusername/chatagent.git

# Or if using SSH (recommended)
git remote add origin git@github.com:yourusername/chatagent.git

# Rename branch to main if needed
git branch -M main

# Commit all files
git add .
git commit -m "Initial commit: Project setup with CI/CD pipeline"

# Push to GitHub
git push -u origin main
```

## Step 3: Configure Repository Settings

### 3.1 Branch Protection Rules

1. Go to **Settings** → **Branches**
2. **Add rule** for `main`:
   - ✅ Require status checks to pass before merging
   - ✅ Require branches to be up to date before merging
   - ✅ Require code reviews before merging (1 approval minimum)
   - ✅ Dismiss stale PR approvals when new commits are pushed
   - ✅ Require status checks to pass: Select `build` workflow

### 3.2 Secrets and Variables

Configure GitHub Secrets for CI/CD (Settings → Secrets and variables → Actions):

```
DOCKER_USERNAME        # Docker Hub username (optional)
DOCKER_PASSWORD        # Docker Hub access token (optional)
SONAR_TOKEN           # SonarCloud token (optional)
```

**To get SonarCloud token** (optional, for code quality):
1. Go to [sonarcloud.io](https://sonarcloud.io)
2. Sign up with GitHub
3. Import repository
4. Go to profile → security → generate token
5. Add as `SONAR_TOKEN` secret

### 3.3 General Settings

1. **Settings** → **General**:
   - ✅ Enable "Discussions" (for community Q&A)
   - ✅ Enable "Issues"
   - ✅ Enable "Projects"
   - ✅ Allow auto-merge
   - ✅ Delete head branches on merge

2. **Settings** → **Collaborators & teams**:
   - Add team members if applicable

3. **Settings** → **Danger Zone** (if private):
   - Enable "Branch protection" for better security

## Step 4: Configure GitHub Actions

The CI/CD workflows are already in `.github/workflows/`:

### Workflows Included

- **build.yml** - Builds and tests on every push/PR
- **code-quality.yml** - Code analysis with SonarCloud (optional)
- **release.yml** - Builds Docker image on releases

### Enable Workflows

1. Go to **Actions** tab
2. Workflows should be listed
3. Click on each and verify they're enabled

### First Workflow Run

1. Go to **Actions** tab
2. Select **Build and Test** workflow
3. Click **Run workflow** → **Run workflow**
4. Watch the build progress in real-time

## Step 5: Setup for Releases and Docker

### 5.1 Configure Docker Hub (Optional)

If you want to publish Docker images:

1. Create [Docker Hub](https://hub.docker.com) account
2. Go to Account Settings → Security → Access Tokens
3. Create new token
4. Add to GitHub Secrets:
   - `DOCKER_USERNAME` = your Docker Hub username
   - `DOCKER_PASSWORD` = the access token

### 5.2 Create First Release

```bash
# Tag a release
git tag -a v0.0.1 -m "Initial release"

# Push tag
git push origin v0.0.1

# Or use GitHub CLI
gh release create v0.0.1 --title "v0.0.1" --notes "Initial release"
```

This will trigger the release workflow to build Docker image.

## Step 6: Configure Branch Strategy

### Recommended Workflow: Git Flow

```
main          ← Production releases
  ↑
  ├── develop  ← Integration branch
  │     ↑
  │     ├── feature/new-feature  ← Feature branches
  │     ├── fix/bug-fix
  │     └── refactor/optimization
```

### Setup in GitHub

1. **Settings** → **Branches**
2. Set `develop` as default branch (optional):
   - Make it easier for contributors
   - Or keep `main` if you prefer

## Step 7: Add Badges to README

Update README.md to show status:

```markdown
# ChatAgent

[![Build Status](https://github.com/yourusername/chatagent/workflows/Build%20and%20Test/badge.svg)](https://github.com/yourusername/chatagent/actions)
[![Code Quality](https://sonarcloud.io/api/project_badges/measure?project=chatagent&metric=alert_status)](https://sonarcloud.io/dashboard?id=chatagent)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21%2B-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.6-green.svg)](https://spring.io/projects/spring-boot)
```

## Step 8: Documentation Checklist

Verify all documentation files are present:

- ✅ **README.md** - Main documentation
- ✅ **SETUP.md** - Development setup guide
- ✅ **CONTRIBUTING.md** - Contribution guidelines
- ✅ **SECURITY.md** - Security policy
- ✅ **LICENSE** - MIT License
- ✅ **CHANGELOG.md** - Version history
- ✅ **.github/workflows/** - CI/CD pipelines
- ✅ **.github/ISSUE_TEMPLATE/** - Issue templates
- ✅ **.github/pull_request_template.md** - PR template

## Step 9: Finalize and Test

### Test the Complete Setup

```bash
# 1. Create a test branch
git checkout -b test/ci-pipeline

# 2. Make a small change
echo "# Test" >> test-file.txt

# 3. Commit and push
git add test-file.txt
git commit -m "test: verify CI pipeline"
git push origin test/ci-pipeline

# 4. Create a Pull Request on GitHub
# Verify workflows run automatically

# 5. Check workflow results
# Go to PR → Checks tab
# All checks should pass

# 6. Delete test branch
git checkout main
git branch -d test/ci-pipeline
git push origin --delete test/ci-pipeline
```

## Step 10: Share and Collaborate

### Share Repository Link

```
https://github.com/yourusername/chatagent
```

### Enable Issues for Bug Reports

1. **Settings** → **Issues** → ✅ Enable
2. Users can click **Issues** tab to report bugs

### Enable Discussions

1. **Settings** → **Discussions** → ✅ Enable
2. Users can ask questions and share ideas

## Useful GitHub URLs for Your Project

- **Repository**: `https://github.com/yourusername/chatagent`
- **Issues**: `https://github.com/yourusername/chatagent/issues`
- **Pull Requests**: `https://github.com/yourusername/chatagent/pulls`
- **Actions**: `https://github.com/yourusername/chatagent/actions`
- **Releases**: `https://github.com/yourusername/chatagent/releases`
- **Discussions**: `https://github.com/yourusername/chatagent/discussions`

## Next Steps

1. ✅ Create the repository
2. ✅ Push code to GitHub
3. ✅ Configure branch protection
4. ✅ Add collaborators (if needed)
5. ✅ Create first release
6. ✅ Monitor workflows
7. ✅ Share with team/community

## Troubleshooting

### Issue: "fatal: 'origin' does not appear to be a 'git' repository"

```bash
# Check remote
git remote -v

# If empty, add remote
git remote add origin https://github.com/yourusername/chatagent.git
```

### Issue: Workflows not running

1. Check **Settings** → **Actions** → Workflows are enabled
2. Verify `.github/workflows/*.yml` files exist
3. Check workflow file syntax

### Issue: Permission denied (publickey)

```bash
# If using SSH, ensure key is added to GitHub
ssh -T git@github.com

# Or use HTTPS instead
git remote set-url origin https://github.com/yourusername/chatagent.git
```

## More Resources

- 📖 [GitHub Docs](https://docs.github.com)
- 🔐 [GitHub Security](https://github.com/security)
- ⚙️ [GitHub Actions](https://github.com/features/actions)
- 📝 [GitHub Guides](https://guides.github.com)

---

**Your ChatAgent project is now ready for professional development!** 🚀
