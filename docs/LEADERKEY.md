# LeaderKey IntelliJ Commands

Since LeaderKey can execute terminal commands, you can directly trigger IntelliJ actions without any URL handler!

## Direct curl Commands

Add these commands to your LeaderKey configuration:

### Single Actions
```bash
curl -s "http://localhost:63342/api/intellij-actions/execute?action=About"
curl -s "http://localhost:63342/api/intellij-actions/execute?action=ShowSettings"
curl -s "http://localhost:63342/api/intellij-actions/execute?action=SaveAll"
curl -s "http://localhost:63342/api/intellij-actions/execute?action=ReformatCode"
curl -s "http://localhost:63342/api/intellij-actions/execute?action=OptimizeImports"
curl -s "http://localhost:63342/api/intellij-actions/execute?action=FindInPath"
curl -s "http://localhost:63342/api/intellij-actions/execute?action=GotoDeclaration"
curl -s "http://localhost:63342/api/intellij-actions/execute?action=Run"
curl -s "http://localhost:63342/api/intellij-actions/execute?action=Debug"
```

### Multiple Actions (executed in sequence)
```bash
curl -s "http://localhost:63342/api/intellij-actions/execute?actions=SaveAll,ReformatCode"
curl -s "http://localhost:63342/api/intellij-actions/execute?actions=SaveAll,OptimizeImports,ReformatCode"
```

### With Delay Between Actions (milliseconds)
```bash
curl -s "http://localhost:63342/api/intellij-actions/execute?actions=SaveAll,ReformatCode&delay=500"
```

## Using the CLI Tool (Recommended)

Install the `ij` command:
```bash
# Copy to home directory (recommended for LeaderKey)
cp cli/ij ~/ij
chmod +x ~/ij
```

Then use these short commands in LeaderKey:
```bash
~/ij About
~/ij ShowSettings
~/ij SaveAll
~/ij ReformatCode
~/ij SaveAll ReformatCode
~/ij SaveAll OptimizeImports ReformatCode
```

## Common IntelliJ Action IDs

| Action | ID | Description |
|--------|-----|-------------|
| Save All | `SaveAll` | Save all modified files |
| Reformat Code | `ReformatCode` | Format current file |
| Optimize Imports | `OptimizeImports` | Remove unused imports |
| Find in Path | `FindInPath` | Search in project |
| Go to Declaration | `GotoDeclaration` | Jump to definition |
| Run | `Run` | Run current configuration |
| Debug | `Debug` | Debug current configuration |
| Show Settings | `ShowSettings` | Open preferences |
| Toggle Bookmark | `ToggleBookmark` | Add/remove bookmark |
| Recent Files | `RecentFiles` | Show recently opened files |
| Search Everywhere | `SearchEverywhere` | Global search |
| Extract Method | `ExtractMethod` | Extract selected code to method |
| Rename | `RenameElement` | Rename symbol |
| Find Usages | `FindUsages` | Find all usages |
| Generate | `Generate` | Generate code menu |

## Finding More Action IDs

1. In IntelliJ: Press `Cmd+Shift+A` (Find Action)
2. Search for the action you want
3. The action ID appears in the search results tooltip

## Requirements

- IntelliJ IDEA must be running
- Action Executor plugin (v1.0.7) must be installed
- IntelliJ REST API is available on port 63342

## Benefits of This Approach

✅ No URL handlers needed  
✅ No Swift/AppleScript apps  
✅ Direct HTTP communication  
✅ Works immediately, every time  
✅ No macOS permission issues  
✅ Simple terminal commands  
✅ Can chain multiple actions  