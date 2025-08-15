# IntelliJ Action Executor - Architecture Documentation

## Version 1.1.3

## Table of Contents
1. [System Overview](#system-overview)
2. [Architecture Components](#architecture-components)
3. [Data Flow](#data-flow)
4. [Key Design Decisions](#key-design-decisions)
5. [Technical Implementation](#technical-implementation)
6. [Security Considerations](#security-considerations)
7. [Performance Optimizations](#performance-optimizations)
8. [Future Considerations](#future-considerations)

## System Overview

The IntelliJ Action Executor plugin enables programmatic execution of IntelliJ IDEA actions via HTTP REST APIs. It was specifically designed to integrate with [LeaderKey.app](https://github.com/arabshapt/LeaderKey.app) for keyboard-free action triggering, but can be used with any HTTP client or automation tool.

### Core Capabilities
- Execute any IntelliJ action by its ID
- Chain multiple actions with configurable delays
- Dual HTTP server architecture (ports 63342 and 63343)
- Thread-safe EDT execution
- Comprehensive context creation for action execution
- Rate limiting bypass via custom server
- CLI tool with automatic fallback

## Architecture Components

### 1. ActionExecutorService
**Location:** `com.leaderkey.intellij.ActionExecutorService`  
**Type:** Application-level Service  
**Purpose:** Core service responsible for executing IntelliJ actions

#### Key Features:
- **EDT-Safe Execution**: All UI operations run on Event Dispatch Thread
- **Synchronous/Asynchronous Modes**: Based on action type (dialog vs. non-dialog)
- **Comprehensive DataContext**: Creates rich context for action execution
- **Action Validation**: Checks if actions are enabled before execution
- **Chain Execution**: Sequential execution with configurable delays

#### Implementation Details:
```kotlin
// Uses ataman-inspired approach (v1.1.2+)
val event = AnActionEvent(
    null,
    dataContext,
    ActionPlaces.KEYBOARD_SHORTCUT,  // Always use keyboard shortcut context
    presentation,
    actionManager,
    0
)
ActionUtil.performDumbAwareUpdate(action, event, true)
ActionUtil.invokeAction(action, dataContext, ActionPlaces.KEYBOARD_SHORTCUT, null, null)
```

### 2. Dual HTTP Server Architecture

#### ActionRestService (Built-in, Port 63342)
**Location:** `com.leaderkey.intellij.ActionRestService`  
**Type:** IntelliJ RestService Extension  
**Purpose:** Primary REST API using IntelliJ's built-in HTTP server

**Characteristics:**
- Integrated with IntelliJ's infrastructure
- Subject to rate limiting (429 errors)
- Automatic registration via plugin.xml
- Trusted host validation (localhost only)

#### CustomHttpServer (Custom, Port 63343)
**Location:** `com.leaderkey.intellij.CustomHttpServer`  
**Type:** Standalone HTTP Server (com.sun.net.httpserver)  
**Purpose:** Bypass rate limiting for high-frequency requests

**Characteristics:**
- No rate limiting
- 10-thread executor pool
- Lightweight JSON serialization
- CORS enabled for browser access
- Started via PluginStartup activity

### 3. Plugin Initialization

#### PluginStartup
**Location:** `com.leaderkey.intellij.PluginStartup`  
**Type:** ProjectActivity  
**Purpose:** Initialize custom HTTP server on project load

#### CustomHttpServerService
**Location:** `com.leaderkey.intellij.CustomHttpServerService`  
**Type:** Application Service  
**Purpose:** Manage custom server lifecycle

### 4. CLI Tool
**Location:** `cli/ij`  
**Type:** Bash script  
**Purpose:** Command-line interface for action execution

**Features:**
- Automatic server selection (custom → built-in fallback)
- Multiple action support
- Success/failure indicators
- Rate limiting detection

## Data Flow

### Request Flow
```
User/LeaderKey → CLI Tool → HTTP Request
                     ↓
            [Try Custom Server :63343]
                     ↓
            [Fallback to Built-in :63342]
                     ↓
            REST Handler (ActionRestService/CustomHttpServer)
                     ↓
            ActionExecutorService
                     ↓
            EDT Thread Execution
                     ↓
            DataContext Creation
                     ↓
            Action Validation & Execution
                     ↓
            JSON Response
```

### EDT Thread Management
```
HTTP Thread → invokeAndWait/invokeLater → EDT Thread
                                              ↓
                                     [Create DataContext]
                                              ↓
                                     [Update Action State]
                                              ↓
                                     [Execute Action]
```

## Key Design Decisions

### 1. Ataman-Inspired Execution (v1.1.2)
**Problem:** Context-dependent actions (like CopyPaths) were failing  
**Solution:** Adopted ataman-intellij's approach:
- Always use `ActionPlaces.KEYBOARD_SHORTCUT`
- Use `ActionUtil.performDumbAwareUpdate()`
- Execute via `ActionUtil.invokeAction()`

**Rationale:** Actions behave exactly as if triggered by keyboard shortcuts, ensuring proper context.

### 2. EDT Thread Safety (v1.1.3)
**Problem:** "Access is allowed from EDT only" errors  
**Solution:** Moved all UI operations into EDT-safe blocks

```kotlin
val executeOnEdt = Runnable {
    // All UI operations here
    val dataContext = createComprehensiveDataContext(project)
    ActionUtil.invokeAction(action, dataContext, ...)
}
ApplicationManager.getApplication().invokeAndWait(executeOnEdt)
```

### 3. Dual Server Architecture (v1.0.8)
**Problem:** IntelliJ's built-in server rate limits (429 errors)  
**Solution:** Custom HTTP server on different port

**Benefits:**
- No rate limiting for automation
- Fallback to built-in server
- Minimal resource overhead

### 4. Comprehensive DataContext
**Problem:** Actions require specific context to be enabled  
**Solution:** Create rich DataContext with all available information:

```kotlin
private fun createComprehensiveDataContext(project: Project?): DataContext {
    val builder = SimpleDataContext.builder()
        .add(CommonDataKeys.PROJECT, project)
        .add(CommonDataKeys.EDITOR, editor)
        .add(CommonDataKeys.VIRTUAL_FILE, virtualFile)
        .add(CommonDataKeys.VIRTUAL_FILE_ARRAY, selectedFiles)
        .add(CommonDataKeys.PSI_FILE, psiFile)
        .add(LangDataKeys.MODULE, module)
        // ... more context
    return builder.build()
}
```

## Technical Implementation

### Action Execution Categories

1. **Dialog Actions**: Execute asynchronously
   - About, ShowSettings, Git.CompareWithBranch
   - Use `invokeLater` for non-blocking execution

2. **UI Actions**: Require UI update delays
   - ActivateProjectToolWindow, SplitVertically
   - Add 50ms delay after execution

3. **Regular Actions**: Execute synchronously in chains
   - ReformatCode, OptimizeImports
   - Use `invokeAndWait` for sequential execution

### JSON Serialization
Custom implementation to avoid library conflicts:
```kotlin
private fun toJson(data: Any?): String {
    return when (data) {
        null -> "null"
        is Boolean -> data.toString()
        is Number -> data.toString()
        is String -> "\"${data.replace("\"", "\\\"")}\""
        is Map<*, *> -> // Recursive map serialization
        is List<*> -> // Recursive list serialization
        else -> toJson(data.toString())
    }
}
```

### Error Handling
- Action not found: Returns error with actionId
- Action disabled: Returns context details in logs
- Execution exceptions: Caught and returned in response
- Connection failures: CLI tool provides fallback

## Security Considerations

1. **Localhost Only**: Both servers only accept localhost connections
2. **No Authentication**: Relies on local-only access
3. **Input Validation**: Action IDs validated against ActionManager
4. **No Code Execution**: Only pre-defined IntelliJ actions

## Performance Optimizations

1. **Thread Pooling**: 10-thread executor for concurrent requests
2. **Lazy Service Loading**: Services loaded on-demand
3. **Action Caching**: ActionManager caches action instances
4. **Minimal JSON Processing**: Custom lightweight serialization
5. **Smart Delays**: Only for UI-affecting actions

## Future Considerations

### Potential Enhancements
1. **WebSocket Support**: For bidirectional communication
2. **Action Macros**: Save and replay action sequences
3. **Context Persistence**: Remember last execution context
4. **OAuth/Token Auth**: For network deployments
5. **Action Discovery**: Better action search/filtering
6. **Execution History**: Track and replay recent actions
7. **Conditional Execution**: Execute based on IDE state
8. **Plugin API**: Allow other plugins to register handlers

### Known Limitations
1. Some actions require specific project types
2. Modal dialogs can block execution chains
3. No support for actions requiring user input
4. Context creation may miss specialized requirements
5. No built-in undo/rollback mechanism

## Version Evolution

### v1.0.0 - Initial Release
- Basic REST API implementation
- Single action execution

### v1.0.8 - Rate Limiting Fix
- Added custom HTTP server (port 63343)
- Dual server architecture
- CLI tool with fallback

### v1.1.0 - Context Enhancement
- Comprehensive DataContext creation
- Better action availability checking
- Added support for file operations and VCS actions

### v1.1.2 - Ataman Approach  
**Problem:** Context-dependent actions like CopyPaths were failing due to incorrect action context  
**Solution:** Adopted ataman-intellij plugin's proven execution pattern

**Key Changes:**
- Always use `ActionPlaces.KEYBOARD_SHORTCUT` for consistent context
- Replace direct `action.actionPerformed()` with `ActionUtil.invokeAction()`
- Add `ActionUtil.performDumbAwareUpdate()` before execution
- Actions now behave exactly as if triggered via keyboard shortcuts

**Implementation:**
```kotlin
// Create event with KEYBOARD_SHORTCUT place
val event = AnActionEvent(
    null,
    dataContext,
    ActionPlaces.KEYBOARD_SHORTCUT,  // Critical for proper context
    presentation,
    actionManager,
    0
)

// Update and invoke using ActionUtil
ActionUtil.performDumbAwareUpdate(action, event, true)
ActionUtil.invokeAction(action, dataContext, ActionPlaces.KEYBOARD_SHORTCUT, null, null)
```

### v1.1.3 - EDT Threading Fix
**Problem:** "Access is allowed from Event Dispatch Thread (EDT) only" errors when executing actions  
**Root Cause:** DataContext creation and action execution happening on HTTP dispatcher threads

**Solution:** Comprehensive EDT-safe execution wrapper

**Key Changes:**
- Wrapped all UI operations in EDT-safe Runnable blocks
- Use `invokeAndWait` for synchronous chain execution
- Use `invokeLater` for async/dialog actions
- Moved DataContext creation inside EDT execution

**Implementation:**
```kotlin
val executeOnEdt = Runnable {
    try {
        // All UI operations now inside EDT
        val project = getActiveProject()
        val dataContext = createComprehensiveDataContext(project)
        
        // Create event and execute action - all on EDT
        val event = AnActionEvent(...)
        ActionUtil.performDumbAwareUpdate(action, event, true)
        ActionUtil.invokeAction(action, dataContext, ...)
    } catch (e: Exception) {
        LOG.error("Error executing action", e)
    }
}

// Execute based on action type
if (waitForCompletion && !isDialogAction(actionId)) {
    ApplicationManager.getApplication().invokeAndWait(executeOnEdt)
} else {
    ApplicationManager.getApplication().invokeLater(executeOnEdt)
}
```

**Results:**
- Eliminated all EDT violation errors
- Context-dependent actions (CopyPaths, Git operations) work correctly
- Stable execution for both single and chained actions

## Conclusion

The IntelliJ Action Executor plugin represents a robust solution for programmatic action execution in IntelliJ IDEA. Through iterative improvements and careful attention to IntelliJ's threading and context requirements, it now provides reliable, high-performance action execution suitable for automation tools and keyboard-enhancement applications like LeaderKey.

The architecture balances simplicity with functionality, providing multiple access methods (REST API, CLI) while maintaining IntelliJ platform best practices for action execution and thread safety.