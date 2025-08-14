# Usage Guide

## Overview

IntelliJ Action Executor allows you to trigger IntelliJ IDEA actions programmatically through:
- Direct HTTP requests (curl, wget, etc.)
- CLI tool (`ij` command)
- Integration with automation tools (LeaderKey, Keyboard Maestro, Alfred, etc.)

## Using the REST API

### Basic Usage

Execute a single action:
```bash
curl "http://localhost:63342/api/intellij-actions/execute?action=ReformatCode"
```

### Chaining Actions

Execute multiple actions in sequence:
```bash
curl "http://localhost:63342/api/intellij-actions/execute?actions=SaveAll,OptimizeImports,ReformatCode"
```

### Adding Delays

Add delay (in milliseconds) between actions:
```bash
curl "http://localhost:63342/api/intellij-actions/execute?actions=SaveAll,ReformatCode&delay=500"
```

### Checking Action Availability

Check if an action exists:
```bash
curl "http://localhost:63342/api/intellij-actions/check?action=ReformatCode"
```

### Listing All Actions

Get a list of all available actions:
```bash
curl "http://localhost:63342/api/intellij-actions/list"
```

## Using the CLI Tool

### Installation

```bash
# Install globally
sudo cp cli/ij /usr/local/bin/ij
sudo chmod +x /usr/local/bin/ij

# Or install locally
cp cli/ij ~/ij
chmod +x ~/ij
```

### Basic Commands

```bash
# Single action
ij ReformatCode

# Multiple actions
ij SaveAll ReformatCode OptimizeImports

# Show usage
ij
```

## Common Workflows

### Code Cleanup

Clean up your code before committing:
```bash
ij SaveAll OptimizeImports ReformatCode
```

### Quick Navigation

```bash
# Go to declaration
ij GotoDeclaration

# Find usages
ij FindUsages

# Search everywhere
ij SearchEverywhere
```

### Refactoring

```bash
# Rename element
ij RenameElement

# Extract method
ij ExtractMethod

# Safe delete
ij SafeDelete
```

### Version Control

```bash
# Commit changes
ij CheckinProject

# Push to remote
ij Vcs.Push

# Pull from remote
ij Vcs.UpdateProject
```

## Integration Examples

### LeaderKey.app

In LeaderKey settings, add shell commands:

```yaml
# Code formatting
leader + c + f: ~/ij ReformatCode

# Save all
leader + s: ~/ij SaveAll

# Clean code (multiple actions)
leader + c + c: ~/ij SaveAll OptimizeImports ReformatCode

# Git operations
leader + g + c: ~/ij CheckinProject
leader + g + p: ~/ij Vcs.Push
```

### Keyboard Maestro

Create a macro with "Execute Shell Script" action:

```bash
/usr/local/bin/ij ReformatCode
```

Or use curl directly:
```bash
curl -s "http://localhost:63342/api/intellij-actions/execute?action=ReformatCode"
```

### Alfred Workflow

1. Create a new workflow
2. Add a "Keyword" input
3. Add a "Run Script" action with:
```bash
~/ij {query}
```
4. Now you can type: `ij ReformatCode` in Alfred

### Raycast

Create a script command:

```bash
#!/bin/bash

# Required parameters:
# @raycast.schemaVersion 1
# @raycast.title Reformat Code
# @raycast.mode silent

~/ij ReformatCode
echo "Code reformatted"
```

### Shell Aliases

Add to your `~/.zshrc` or `~/.bashrc`:

```bash
# IntelliJ shortcuts
alias ijformat="ij ReformatCode"
alias ijsave="ij SaveAll"
alias ijclean="ij SaveAll OptimizeImports ReformatCode"
alias ijcommit="ij CheckinProject"
alias ijfind="ij FindInPath"
```

### Git Hooks

Create a pre-commit hook (`.git/hooks/pre-commit`):

```bash
#!/bin/bash
# Format code before commit
~/ij SaveAll OptimizeImports ReformatCode
```

## Finding Action IDs

### Method 1: IntelliJ UI

1. In IntelliJ IDEA, press `Cmd+Shift+A` (macOS) or `Ctrl+Shift+A` (Windows/Linux)
2. Type the name of the action you want
3. The action ID appears in the tooltip

### Method 2: API Endpoint

```bash
# List all actions
curl "http://localhost:63342/api/intellij-actions/list" | grep -i "format"

# Check specific action
curl "http://localhost:63342/api/intellij-actions/check?action=ReformatCode"
```

### Method 3: Action ID Reference

See our [complete list of common action IDs](ACTIONS.md).

## Tips and Tricks

### Context Matters

Some actions require specific context:
- Editor actions need an open file
- VCS actions need a project with version control
- Run/Debug actions need run configurations

### Batch Operations

Process multiple files efficiently:
```bash
# Save all, optimize imports, reformat all modified files
ij SaveAll OptimizeImports ReformatCode
```

### Custom Scripts

Create custom scripts for complex workflows:

```bash
#!/bin/bash
# cleanup.sh - Complete code cleanup

echo "🧹 Starting code cleanup..."
~/ij SaveAll
echo "✓ Files saved"

~/ij OptimizeImports
echo "✓ Imports optimized"

~/ij ReformatCode
echo "✓ Code reformatted"

echo "✨ Cleanup complete!"
```

### Error Handling

Check action execution results:

```bash
if ~/ij ReformatCode; then
    echo "✓ Code formatted successfully"
else
    echo "✗ Failed to format code"
fi
```

## Troubleshooting

### Action Not Found

- Verify the action ID is correct (case-sensitive)
- Use the `/check` endpoint to verify: `curl "http://localhost:63342/api/intellij-actions/check?action=ActionId"`

### Action Not Executing

- Some actions require specific context (file open, selection, etc.)
- Check IntelliJ's status bar for error messages
- Ensure IntelliJ has focus for UI-dependent actions

### Connection Refused

- Verify IntelliJ IDEA is running
- Check the plugin is enabled
- Ensure port 63342 is not blocked

### Slow Response

- First action after IntelliJ starts may be slower
- Complex actions (like reformatting large files) take time
- Check IntelliJ's memory usage

## Next Steps

- [API Reference](API.md) - Complete API documentation
- [LeaderKey Integration](LEADERKEY.md) - Detailed LeaderKey setup
- [Common Actions](ACTIONS.md) - List of useful action IDs