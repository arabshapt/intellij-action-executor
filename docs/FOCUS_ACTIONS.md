# Focus Actions Documentation

## Overview

This document describes the focus-switching actions available in the IntelliJ Action Executor plugin. These actions allow you to programmatically control focus between different components of the IntelliJ IDE, particularly between the editor and various tool windows.

## Available Focus Actions

### Universal Focus Action

#### `FocusEditor` ⭐ **Recommended**
**The universal action to focus the editor from anywhere.** This is the most reliable way to return focus to the editor regardless of your current context.

**Features:**
- Works from any tool window (Terminal, Project view, etc.)
- Doesn't hide tool windows (unlike `HideAllWindows`)
- Opens a recent file if no editor is currently open
- Multiple fallback strategies for maximum reliability

**Usage:**
```bash
ij FocusEditor
```

This single command will always bring focus back to the editor, making it perfect for keyboard shortcuts and macros.

### Terminal Focus Actions

#### `Terminal.MoveToEditor`
Moves focus from the terminal to the editor.

**Prerequisites:**
- Terminal tool window should be open
- An editor should be available

**Usage:**
```bash
ij Terminal.MoveToEditor
```

**Conditional usage:**
```bash
ij --if focusInTerminal --then Terminal.MoveToEditor
```

#### `Terminal.Escape`
Escapes from the terminal, typically moving focus to the editor.

**Usage:**
```bash
ij Terminal.Escape
```

### Editor Focus Actions

#### `EditorEscape`
Handles escape in the editor context. This action:
- Closes autocomplete popups
- Cancels current operations
- Clears selections
- Can return focus to editor from tool windows

**Usage:**
```bash
ij EditorEscape
```

### Window Navigation Actions

#### `JumpToLastWindow`
Toggles between the last two focused windows. If you're in the editor, it switches to the last active tool window. If you're in a tool window, it switches back to the editor.

**Usage:**
```bash
ij JumpToLastWindow
```

#### `HideActiveWindow`
Hides the currently active tool window and moves focus to the editor.

**Usage:**
```bash
ij HideActiveWindow
```

#### `HideAllWindows`
Hides all tool windows and focuses the editor.

**Usage:**
```bash
ij HideAllWindows
```

## Focus State Conditions

You can check the current focus state using these conditions:

### `focusInEditor`
Returns `true` if focus is currently in the editor.

```bash
ij --if focusInEditor --then SaveAll
```

### `focusInTerminal`
Returns `true` if focus is currently in the terminal.

```bash
ij --if focusInTerminal --then Terminal.MoveToEditor
```

### `focusInToolWindow`
Returns `true` if focus is in any tool window.

```bash
ij --if focusInToolWindow --then JumpToLastWindow
```

### `focusInProject`
Returns `true` if focus is in the Project tool window.

```bash
ij --if focusInProject --then CollapseAll
```

### `hasFocus`
Returns `true` if the IDE window has OS-level focus.

```bash
ij --if hasFocus --then SaveAll
```

## Focus-Specific Tool Windows

You can also check focus for specific tool windows:

```bash
ij --if focusInToolWindow:Database --then DatabaseRefresh
ij --if Terminal:focus --then Terminal.MoveToEditor
ij --if Project:focus --then CollapseAll
```

## Common Use Cases

### Return to editor from anywhere (simplest approach) ⭐
```bash
ij FocusEditor
```

### Auto-save when switching from editor
```bash
ij --if focusInEditor --then SaveAll ActivateTerminalToolWindow
```

### Return to editor from any tool window
```bash
# Option 1: Universal approach (recommended)
ij FocusEditor

# Option 2: Conditional approach
ij --if focusInToolWindow --then FocusEditor
```

### Clear terminal and return to editor
```bash
ij --if focusInTerminal --then TerminalClearBuffer Terminal.MoveToEditor
```

### Toggle terminal visibility
```bash
ij --if terminal --then HideActiveWindow --else ActivateTerminalToolWindow
```

### Smart escape - context-aware escape action
```bash
ij --if focusInTerminal --then Terminal.Escape --else EditorEscape
```

## Checking Focus State via API

You can query the current focus state using the REST API:

```bash
curl "http://localhost:63343/api/intellij-actions/state/query?checks=focusInEditor,focusInTerminal,focusInToolWindow"
```

Response:
```json
{
  "focusInEditor": true,
  "focusInTerminal": false,
  "focusInToolWindow": false
}
```

## Troubleshooting

### Focus actions not working

1. **Check IDE has focus**: The IDE window must have OS-level focus for focus actions to work properly.
   ```bash
   curl "http://localhost:63343/api/intellij-actions/state/query?checks=hasFocus"
   ```

2. **Verify target exists**: Ensure the target you're trying to focus exists (e.g., editor is open, terminal is available).
   ```bash
   curl "http://localhost:63343/api/intellij-actions/state/query?checks=editor,terminal"
   ```

3. **Use conditional execution**: Wrap focus actions in conditions to ensure they only execute when appropriate.
   ```bash
   ij --if terminal --then Terminal.MoveToEditor
   ```

### Focus not switching as expected

The plugin now includes enhanced focus management that:
- Properly detects terminal focus context
- Provides fallback mechanisms when primary focus methods fail
- Logs focus state before and after actions for debugging

If focus switching still doesn't work:
1. Check the IntelliJ logs for detailed focus state information
2. Try using `JumpToLastWindow` as an alternative
3. Use `HideActiveWindow` or `HideAllWindows` to force focus to editor

### Context issues with REST API

When actions are executed via REST API, the context might not reflect the actual UI state. The plugin now includes:
- Enhanced context detection for terminal focus
- Special handling for focus-switching actions
- Dedicated FocusManagementService for reliable focus operations

## Implementation Details

The focus switching functionality is implemented through:

1. **ActionExecutorService**: Detects and specially handles focus-switching actions
2. **FocusManagementService**: Provides dedicated methods for focus operations
3. **Enhanced Context Detection**: Properly captures terminal and tool window focus states
4. **Fallback Mechanisms**: Multiple strategies to ensure focus switches successfully

## Testing

Run the focus test suite to verify all focus actions are working:

```bash
./test/test-terminal-focus.sh
```

This will test:
- Terminal to editor focus switching
- Editor escape functionality
- Window navigation
- Conditional focus switching
- Error cases and edge conditions