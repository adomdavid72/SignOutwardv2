# Install GitHub CLI on macOS

## Option 1: Install via Homebrew (Recommended)

### Step 1: Install Homebrew (if not installed)

Run this command in Terminal (it will prompt for your password):

```bash
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
```

Follow the on-screen prompts. After installation, you may need to add Homebrew to your PATH. The installer will provide instructions.

### Step 2: Install GitHub CLI

After Homebrew is installed, run:

```bash
brew install gh
```

### Step 3: Verify Installation

```bash
gh --version
```

### Step 4: Authenticate with GitHub

```bash
gh auth login
```

Follow the prompts to authenticate (you can choose browser or token authentication).

## Option 2: Install via Direct Download

1. Visit: https://github.com/cli/cli/releases/latest
2. Download the macOS `.pkg` file
3. Open the downloaded file and follow the installation wizard
4. Verify installation: `gh --version`
5. Authenticate: `gh auth login`

## Option 3: Install via MacPorts (if you use MacPorts)

```bash
sudo port install gh
```

## Quick Setup After Installation

Once GitHub CLI is installed, authenticate:

```bash
gh auth login
```

You'll be prompted to:
1. Choose authentication method (browser or token)
2. Select your preferred protocol (HTTPS or SSH)
3. Authenticate in browser or paste token

## Verify Installation

After installation and authentication:

```bash
# Check version
gh --version

# Check authentication status
gh auth status

# Test by listing your repositories
gh repo list
```

## Common GitHub CLI Commands

```bash
# Clone a repository
gh repo clone <owner>/<repo>

# Create a new repository
gh repo create <name>

# View issues
gh issue list

# Create a pull request
gh pr create

# View pull requests
gh pr list
```

