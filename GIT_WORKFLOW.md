# Git Workflow Rules for SignOutwardv2

## Rules

1. **All commits must go to 'develop' branch only**
2. **Never commit directly to 'main' branch**
3. **Always scan for secrets before committing**
4. **Auto-switch to 'develop' if on another branch**

## Usage

### Option 1: Using the Script (Recommended)

```bash
./scripts/commit-to-develop.sh "Your commit message here"
```

The script will:
- ✅ Automatically switch to 'develop' if needed
- ✅ Scan for secrets before committing
- ✅ Stage all changes
- ✅ Commit with your message
- ✅ Push to origin/develop

### Option 2: Manual Git Commands

If you prefer manual control:

```bash
# 1. Check current branch
git branch --show-current

# 2. Switch to develop if needed
git checkout develop

# 3. Pull latest (recommended)
git pull origin develop

# 4. Stage changes
git add .

# 5. Commit
git commit -m "Your commit message"

# 6. Push
git push origin develop
```

## Workflow Overview

```
main (production)
  ↑
  | (merge via PR)
  |
develop (development) ← All new commits go here
  ↑
  | (you are here)
  |
local changes
```

## Branch Strategy

- **main**: Production-ready code only
  - Protected branch
  - Only updated via pull requests from 'develop'
  - Never commit directly to main

- **develop**: Development branch
  - All new commits go here
  - Feature development
  - Testing and integration

## Secret Detection

The workflow script automatically scans for GitHub tokens (`ghp_`) before committing. If found:
- ⚠️ Warning is displayed
- You can choose to continue or cancel
- Secrets should be removed before committing

## Examples

```bash
# Feature development
./scripts/commit-to-develop.sh "Add user authentication feature"

# Bug fix
./scripts/commit-to-develop.sh "Fix playback issue with mixed media"

# Documentation
./scripts/commit-to-develop.sh "Update README with installation instructions"
```

## Important Notes

1. **Never commit secrets**: Always remove tokens, API keys, etc. before committing
2. **Pull before push**: The script pulls latest changes to avoid conflicts
3. **Meaningful messages**: Write clear commit messages describing your changes
4. **Regular commits**: Commit often with small, logical changes

## Troubleshooting

### "Branch is behind" error
```bash
git pull origin develop
git push origin develop
```

### "Push rejected" error
Check if you have the latest changes:
```bash
git pull --rebase origin develop
git push origin develop
```

### Accidentally committed to main
```bash
# Switch to develop
git checkout develop

# Cherry-pick the commit
git cherry-pick <commit-hash>

# Remove from main (if not pushed)
git checkout main
git reset --hard HEAD~1
```

