# Steps to Create GitHub Repository and Push Code

## Option 1: Create Repository on GitHub Web (Recommended)

### Step 1: Create Repository on GitHub

1. Go to **https://github.com/new**
2. Fill in the form:
   - **Repository name**: `chatagent`
   - **Description**: `AI-powered chat application with Spring Boot and PGVector`
   - **Visibility**: Select `Public` (recommended) or `Private`
   - **Initialize**: ✗ (uncheck - we already have code)
3. Click **Create repository**
4. You'll see a page with instructions. Note the repository URL

### Step 2: Add Remote and Push

Copy one of the following commands based on your auth preference:

**Using HTTPS (Easier):**
```bash
cd /Users/yuqiguo/Desktop/chatagent
git remote add origin https://github.com/chatagent/chatagent.git
git branch -M main
git push -u origin main
```

**Using SSH (More Secure):**
```bash
cd /Users/yuqiguo/Desktop/chatagent
git remote add origin git@github.com:chatagent/chatagent.git
git branch -M main
git push -u origin main
```

### Step 3: Verify

1. Go to `https://github.com/chatagent/chatagent`
2. You should see all your files pushed ✅

---

## Option 2: Use GitHub CLI (After Authentication)

If you successfully authenticate with `gh auth login`:

```bash
cd /Users/yuqiguo/Desktop/chatagent
gh repo create chatagent \
  --public \
  --source=. \
  --remote=origin \
  --push \
  --description="AI-powered chat application with Spring Boot and PGVector"
```

---

## Important Notes

- Replace `chatagent` with your actual GitHub username if using HTTPS/SSH
- For HTTPS: You'll need a Personal Access Token (PAT)
  - Generate at: https://github.com/settings/tokens
  - Click "Generate new token" → "Tokens (classic)"
  - Select scopes: `repo`, `workflow`, `admin:repo_hook`
  - Use this token as password when pushing
  
- For SSH: Ensure your SSH key is added to GitHub
  - Check: https://github.com/settings/keys
  - If not there, generate with: `ssh-keygen -t ed25519 -C "your_email@example.com"`

---

## Quick Commands Summary

Once authenticated, execute these commands in order:

```bash
# 1. Navigate to project
cd /Users/yuqiguo/Desktop/chatagent

# 2. Add remote (use HTTPS URL from GitHub)
git remote add origin https://github.com/YOUR_USERNAME/chatagent.git

# 3. Set main branch
git branch -M main

# 4. Push code
git push -u origin main
```

---

## Troubleshooting

**Error: "fatal: remote origin already exists"**
- Remove existing remote: `git remote remove origin`
- Then add the new one

**Error: "fatal: 'origin' does not appear to be a 'git' repository"**
- Check remotes: `git remote -v`
- Add remote if missing

**Error: "Permission denied (publickey)"**
- Using SSH but key not set up
- Switch to HTTPS or add SSH key to GitHub

**Error: "fatal: Could not read from remote repository"**
- Check internet connection
- Verify URL is correct
- Check GitHub credentials

---

## What's Next After Pushing

1. ✅ Go to repository on GitHub
2. ✅ Configure branch protection (Settings → Branches)
3. ✅ Add repository secrets for CI/CD (Settings → Secrets)
   - `DOCKER_USERNAME` (Docker Hub username, if using Docker publish)
   - `DOCKER_PASSWORD` (Docker Hub token, if using Docker publish)
4. ✅ Enable Actions (should be automatic)
5. ✅ Make a test commit to verify workflows run

---

**Ready to proceed?** Use the commands above or run the quick setup below.
