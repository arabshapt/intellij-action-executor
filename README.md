# IntelliJ Action Executor

[![Version](https://img.shields.io/badge/version-1.0.8-blue.svg)](https://github.com/arabshapt/intellij-action-executor/releases)
[![IntelliJ Platform](https://img.shields.io/badge/IntelliJ%20Platform-2024.1+-green.svg)](https://www.jetbrains.com/idea/)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

Execute IntelliJ IDEA actions programmatically via REST API. Perfect for integrating with macro tools, keyboard launchers, and automation workflows

## ✨ Features

- 🚀 **REST API** for executing IntelliJ actions
- ⚡ **Zero-latency execution** - actions trigger instantly
- 🔗 **Chain multiple actions** in a single request
- 🎯 **Simple HTTP interface** - works with any HTTP client
- 🛠️ **CLI tool included** for terminal usage
- 🔌 **Perfect integration** with [LeaderKey](https://github.com/arabshapt/LeaderKey.app), Keyboard Maestro, Karabiner, Alfred, etc.
- 🚫 **No rate limiting** - Custom server bypasses IntelliJ's 429 errors (v1.0.8+)

## 🎯 Quick Start

```bash
# Install the plugin in IntelliJ IDEA
# Then use the CLI tool:
curl "http://localhost:63342/api/intellij-actions/execute?action=ReformatCode"

# Or with the included CLI:
~/ij ReformatCode
```

## 📦 Installation

### Option 1: Install from JAR (Recommended)

1. Download the latest `intellijPlugin-1.0.8.zip` from [Releases](https://github.com/arabshapt/intellij-action-executor/releases)
2. In IntelliJ IDEA: `Settings → Plugins → ⚙️ → Install Plugin from Disk...`
3. Select the downloaded ZIP file
4. Restart IntelliJ IDEA

### Option 2: Build from Source

```bash
git clone https://github.com/arabshapt/intellij-action-executor.git
cd intellij-action-executor
./gradlew buildPlugin
```

The plugin will be in `build/distributions/`.

### Install CLI Tool (Optional)

```bash
# Copy to home directory for easy access
cp cli/ij ~/ij
chmod +x ~/ij
```

## 🚀 Usage

### REST API

#### Execute Single Action
```bash
curl "http://localhost:63342/api/intellij-actions/execute?action=ReformatCode"
```

#### Execute Multiple Actions
```bash
curl "http://localhost:63342/api/intellij-actions/execute?actions=SaveAll,ReformatCode,OptimizeImports"
```

#### With Delay Between Actions
```bash
curl "http://localhost:63342/api/intellij-actions/execute?actions=SaveAll,ReformatCode&delay=500"
```

### CLI Tool

```bash
# Single action
~/ij ReformatCode

# Multiple actions
~/ij SaveAll ReformatCode OptimizeImports
```

### LeaderKey.app Configuration

In LeaderKey, configure shell commands:

```json
{
  "sequences": [
    {
      "keys": "i r",
      "name": "Reformat Code",
      "action": "intellij-action://execute?action=ReformatCode"
    },
    {
      "keys": "i o",
      "name": "Optimize Imports",
      "action": "intellij-action://execute?action=OptimizeImports"
    },
    {
      "keys": "i s",
      "name": "Save All",
      "action": "intellij-action://execute?action=SaveAll"
    },
    {
      "keys": "i f",
      "name": "Find in Path",
      "action": "intellij-action://execute?action=FindInPath"
    },
    {
      "keys": "i g d",
      "name": "Go to Declaration",
      "action": "intellij-action://execute?action=GotoDeclaration"
    },
    {
      "keys": "i g i",
      "name": "Go to Implementation",
      "action": "intellij-action://execute?action=GotoImplementation"
    },
    {
      "keys": "i c",
      "name": "Clean Code",
      "action": "intellij-action://execute?actions=SaveAll,ReformatCode,OptimizeImports"
    },
    {
      "keys": "i t r",
      "name": "Run Tests",
      "action": "intellij-action://execute?action=RunClass"
    },
    {
      "keys": "i t d",
      "name": "Debug Tests",
      "action": "intellij-action://execute?action=DebugClass"
    },
    {
      "keys": "i v c",
      "name": "Git Commit",
      "action": "intellij-action://execute?action=CheckinProject"
    },
    {
      "keys": "i v p",
      "name": "Git Push",
      "action": "intellij-action://execute?action=Vcs.Push"
    },
    {
      "keys": "i v u",
      "name": "Git Pull",
      "action": "intellij-action://execute?action=Vcs.UpdateProject"
    },
    {
      "keys": "i w s",
      "name": "Split Window",
      "action": "intellij-action://execute?action=SplitVertically"
    },
    {
      "keys": "i w c",
      "name": "Close Window",
      "action": "intellij-action://execute?action=CloseContent"
    }
  ]
}
```

### Terminal Testing

Test the URL handler from terminal:

```bash
# Test single action
open "intellij-action://execute?action=AboutAction"

# Test multiple actions
open "intellij-action://execute?actions=SaveAll,ReformatCode"
```

## Common Action IDs

### File Operations
- `SaveAll` - Save all files
- `SaveDocument` - Save current file
- `ReloadFromDisk` - Reload file from disk
- `NewFile` - Create new file
- `NewClass` - Create new class
- `CloseContent` - Close current tab
- `CloseAllEditors` - Close all tabs

### Code Editing
- `ReformatCode` - Reformat current file
- `OptimizeImports` - Optimize imports
- `CodeCompletion` - Show code completion
- `CommentByLineComment` - Toggle line comment
- `CommentByBlockComment` - Toggle block comment
- `ExpandAllRegions` - Expand all code regions
- `CollapseAllRegions` - Collapse all code regions

### Navigation
- `GotoDeclaration` - Go to declaration
- `GotoImplementation` - Go to implementation
- `GotoTypeDeclaration` - Go to type declaration
- `GotoSuperMethod` - Go to super method
- `GotoClass` - Go to class
- `GotoFile` - Go to file
- `GotoSymbol` - Go to symbol
- `FindInPath` - Search in project
- `FindUsages` - Find usages
- `ShowUsages` - Show usages popup

### Refactoring
- `RefactoringMenu` - Show refactoring menu
- `RenameElement` - Rename element
- `Move` - Move element
- `Copy` - Copy element
- `SafeDelete` - Safe delete
- `ExtractMethod` - Extract method
- `ExtractVariable` - Extract variable
- `Inline` - Inline variable/method

### Version Control
- `CheckinProject` - Commit changes
- `Vcs.Push` - Push to remote
- `Vcs.UpdateProject` - Pull from remote
- `Vcs.ShowTabbedFileHistory` - Show file history
- `Git.Branches` - Show Git branches
- `Git.Merge` - Merge branches
- `Git.Stash` - Stash changes

### Run/Debug
- `Run` - Run current configuration
- `Debug` - Debug current configuration
- `RunClass` - Run current class
- `DebugClass` - Debug current class
- `Stop` - Stop running process
- `ToggleLineBreakpoint` - Toggle breakpoint
- `ViewBreakpoints` - View all breakpoints

### Window Management
- `SplitVertically` - Split window vertically
- `SplitHorizontally` - Split window horizontally
- `NextSplitter` - Next split window
- `PrevSplitter` - Previous split window
- `MaximizeToolWindow` - Maximize tool window
- `HideAllWindows` - Hide all tool windows

### Project
- `ShowSettings` - Open settings
- `ShowProjectStructureSettings` - Project structure
- `ProjectViewPopupMenu` - Project view menu
- `SynchronizeCurrentFile` - Sync file in project view
- `CollapseAll` - Collapse all in project view
- `ExpandAll` - Expand all in project view

## REST API

The plugin provides REST endpoints at:
- `http://localhost:63343/api/intellij-actions/` - Custom server (no rate limiting, v1.0.8+)
- `http://localhost:63342/api/intellij-actions/` - Built-in IntelliJ API (may have rate limiting):

### Execute Action
```
GET /api/intellij-actions/execute?action=ActionId
GET /api/intellij-actions/execute?actions=Action1,Action2&delay=100
```

### Check Action Availability
```
GET /api/intellij-actions/check?action=ActionId
```

### List Available Actions
```
GET /api/intellij-actions/list
```

### Health Check
```
GET /api/intellij-actions/health
```

## Finding Action IDs

To find the ID of any action in IntelliJ:

1. Open IntelliJ IDEA
2. Press `Cmd+Shift+A` (Find Action)
3. Search for the action you want
4. The action ID will be shown in the status bar at the bottom

Alternatively, use the REST API to list all available actions:
```bash
curl http://localhost:63343/api/intellij-actions/list
```

## Troubleshooting

### Plugin Not Working
1. Ensure IntelliJ IDEA is running
2. Check that the plugin is installed and enabled
3. Verify REST API is accessible: `curl http://localhost:63343/api/intellij-actions/health`

### Rate Limiting Errors (429 Too Many Requests)
1. Upgrade to version 1.0.8+ which includes a custom server without rate limiting
2. The CLI tool automatically uses the custom server on port 63343
3. Direct API calls can use port 63343 instead of 63342

### URL Handler Not Working
1. Check logs at `/tmp/intellij-action-handler.log`
2. Verify URL scheme registration: `/System/Library/Frameworks/CoreServices.framework/Versions/A/Frameworks/LaunchServices.framework/Versions/A/Support/lsregister -dump | grep intellij-action`
3. Try reinstalling: `cd url-handler && ./install.sh`

### Actions Not Executing
1. Some actions require specific context (e.g., editor focus, file selection)
2. Check if the action is enabled in current context
3. Use the `/check` endpoint to verify action availability

## Development

### Building the Plugin
```bash
./gradlew clean buildPlugin
```

### Running IDE with Plugin
```bash
./gradlew runIde
```

### Running Tests
```bash
./gradlew test
```

## Requirements

- IntelliJ IDEA 2024.1 or later
- macOS 10.15 or later
- Python 3.6 or later
- Java 17 or later

## License

MIT License - See LICENSE file for details

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## Credits

Created for integration with [LeaderKey.app](https://github.com/mikker/LeaderKey.app) - The faster than your launcher launcher.