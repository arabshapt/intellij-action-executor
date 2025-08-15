# Installation Guide

## Prerequisites

- IntelliJ IDEA 2024.1 or later
- Java 17 or later (for building from source)

## Installing the Plugin

### Method 1: From Release (Recommended)

1. Download the latest `intellijPlugin-X.X.X.zip` from the [Releases](https://github.com/arabshapt/intellij-action-executor/releases) page

2. Open IntelliJ IDEA

3. Navigate to Settings/Preferences:
   - **macOS**: `IntelliJ IDEA → Preferences` or `Cmd + ,`
   - **Windows/Linux**: `File → Settings` or `Ctrl + Alt + S`

4. Go to `Plugins` in the left sidebar

5. Click the gear icon (⚙️) next to "Installed"

6. Select `Install Plugin from Disk...`

7. Navigate to and select the downloaded `.zip` file

8. Click `OK` and restart IntelliJ IDEA when prompted

### Method 2: Build from Source

1. Clone the repository:
```bash
git clone https://github.com/arabshapt/intellij-action-executor.git
cd intellij-action-executor
```

2. Build the plugin:
```bash
./gradlew clean buildPlugin
```

3. The plugin ZIP will be created in `build/distributions/`

4. Follow steps 2-8 from Method 1 above, using the built ZIP file

## Installing the CLI Tool

The CLI tool (`ij`) provides a convenient way to trigger IntelliJ actions from the terminal.

### For All Users

```bash
# Copy to /usr/local/bin (requires sudo)
sudo cp cli/ij /usr/local/bin/ij
sudo chmod +x /usr/local/bin/ij

# Test it
ij About
```

### For Current User Only

```bash
# Copy to home directory
cp cli/ij ~/ij
chmod +x ~/ij

# Test it
~/ij About
```

### For LeaderKey.app Users

If you're using LeaderKey.app and `/usr/local/bin` is not in PATH:

```bash
# Install to home directory
cp cli/ij ~/ij
chmod +x ~/ij

# In LeaderKey, use commands like:
# ~/ij ReformatCode
# ~/ij SaveAll
```

## Verifying Installation

### Plugin Verification

1. In IntelliJ IDEA, go to `Settings → Plugins → Installed`
2. Look for "Action Executor for LeaderKey"
3. Ensure it's enabled (checkbox is checked)

### API Verification

Test the REST API is working:

```bash
curl http://localhost:63342/api/intellij-actions/health
```

Expected response:
```json
{"status":"healthy","plugin":"intellij-action-executor","version":"1.1.3"}
```

### CLI Verification

If you installed the CLI tool:

```bash
# From /usr/local/bin
ij About

# Or from home directory
~/ij About
```

You should see: `✓ Action executed: About`

## Troubleshooting

### Plugin Not Visible After Installation

1. Ensure you restarted IntelliJ IDEA after installation
2. Check the plugin is compatible with your IntelliJ version (requires 2024.1+)
3. Look in `Settings → Plugins → Installed` and search for "Action Executor"

### REST API Not Responding

1. Verify IntelliJ IDEA is running
2. Check the plugin is enabled in `Settings → Plugins`
3. Ensure no firewall is blocking port 63342
4. Try restarting IntelliJ IDEA

### CLI Tool Not Found

If `ij: command not found`:

1. Use the full path: `/usr/local/bin/ij` or `~/ij`
2. Add to PATH: `echo 'export PATH="/usr/local/bin:$PATH"' >> ~/.zshrc`
3. For LeaderKey, always use full path: `~/ij`

### Actions Not Executing

1. Some actions require specific context (e.g., editor focus)
2. Ensure the action ID is correct (case-sensitive)
3. Check if the action is enabled in the current context
4. Look at IntelliJ's status bar for any error messages

## Uninstalling

### Remove Plugin

1. Go to `Settings → Plugins → Installed`
2. Find "Action Executor for LeaderKey"
3. Click the gear icon next to it
4. Select "Uninstall"
5. Restart IntelliJ IDEA

### Remove CLI Tool

```bash
# If installed globally
sudo rm /usr/local/bin/ij

# If installed in home directory
rm ~/ij
```

## Next Steps

- [Usage Guide](USAGE.md) - Learn how to use the plugin
- [LeaderKey Integration](LEADERKEY.md) - Set up with LeaderKey.app
- [API Reference](API.md) - Full API documentation