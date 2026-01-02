#!/bin/bash
# Git Workflow Script: Commit to Develop Branch
# Ensures all commits go to 'develop' branch only

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Check if commit message provided
if [ -z "$1" ]; then
    echo -e "${RED}Error: Commit message is required${NC}"
    echo "Usage: $0 \"Your commit message\""
    exit 1
fi

COMMIT_MESSAGE="$1"
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

cd "$REPO_ROOT"

echo -e "${GREEN}=== Git Workflow: Commit to Develop Branch ===${NC}"
echo ""

# Step 1: Check current branch
CURRENT_BRANCH=$(git branch --show-current)
echo "Current branch: $CURRENT_BRANCH"

# Step 2: Switch to develop if not already on it
if [ "$CURRENT_BRANCH" != "develop" ]; then
    echo -e "${YELLOW}⚠️  Not on 'develop' branch. Switching to 'develop'...${NC}"
    git checkout develop
    echo -e "${GREEN}✅ Switched to 'develop' branch${NC}"
else
    echo -e "${GREEN}✅ Already on 'develop' branch${NC}"
fi

# Ensure we're up to date
echo ""
echo "Pulling latest changes from origin/develop..."
git pull origin develop || echo "No remote branch or already up to date"

# Step 3: Scan for secrets before staging
echo ""
echo -e "${YELLOW}Scanning for secrets...${NC}"
SECRETS_FOUND=$(grep -r "ghp_" . --exclude-dir=.git --exclude="*.md" 2>/dev/null | wc -l | tr -d ' ')

if [ "$SECRETS_FOUND" -gt 0 ]; then
    echo -e "${RED}⚠️  WARNING: Potential secrets detected!${NC}"
    echo -e "${RED}Please remove secrets before committing.${NC}"
    echo ""
    grep -r "ghp_" . --exclude-dir=.git --exclude="*.md" 2>/dev/null | head -5
    echo ""
    read -p "Continue anyway? (y/N): " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        echo "Commit cancelled."
        exit 1
    fi
else
    echo -e "${GREEN}✅ No secrets detected${NC}"
fi

# Step 4: Stage all changes
echo ""
echo "Staging all changes..."
git add .
STAGED_FILES=$(git diff --cached --name-only | wc -l | tr -d ' ')
echo -e "${GREEN}✅ Staged $STAGED_FILES file(s)${NC}"

# Check if there are changes to commit
if [ "$STAGED_FILES" -eq 0 ]; then
    echo -e "${YELLOW}⚠️  No changes to commit${NC}"
    exit 0
fi

# Step 5: Commit with user's message
echo ""
echo "Committing changes..."
git commit -m "$COMMIT_MESSAGE"
COMMIT_HASH=$(git rev-parse --short HEAD)
echo -e "${GREEN}✅ Committed: $COMMIT_HASH${NC}"
echo "   Message: $COMMIT_MESSAGE"

# Step 6: Push to origin/develop
echo ""
echo "Pushing to origin/develop..."
if git push origin develop; then
    echo ""
    echo -e "${GREEN}═══════════════════════════════════════════════════════════════${NC}"
    echo -e "${GREEN}✅ Success! Changes pushed to origin/develop${NC}"
    echo -e "${GREEN}═══════════════════════════════════════════════════════════════${NC}"
    echo ""
    echo "Commit: $COMMIT_HASH"
    echo "Branch: develop"
    echo "Remote: origin/develop"
    echo ""
else
    echo -e "${RED}❌ Push failed${NC}"
    exit 1
fi

