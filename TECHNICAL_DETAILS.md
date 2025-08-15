# Technical Implementation Details

## Table of Contents
1. [EDT Thread Management](#edt-thread-management)
2. [DataContext Creation](#datacontext-creation)
3. [Action Execution Patterns](#action-execution-patterns)
4. [Ataman-Inspired Approach](#ataman-inspired-approach)
5. [Server Architecture](#server-architecture)
6. [Performance Optimizations](#performance-optimizations)
7. [Error Handling Strategy](#error-handling-strategy)
8. [Known Limitations](#known-limitations)

## EDT Thread Management

### Problem Context
IntelliJ IDEA requires all UI operations to run on the Event Dispatch Thread (EDT). The plugin receives HTTP requests on separate threads, creating thread safety issues when accessing UI components.

### Evolution of Threading Strategy

#### v1.1.2 and Earlier
```kotlin
// ❌ INCORRECT - UI operations on HTTP thread
fun executeAction(actionId: String): ExecutionResult {
    val project = getActiveProject()  // ❌ Can cause EDT violations
    val dataContext = createComprehensiveDataContext(project)  // ❌ UI access
    action.actionPerformed(event)  // ❌ UI operation
}
```

#### v1.1.3 Solution
```kotlin
// ✅ CORRECT - All UI operations wrapped for EDT
fun executeActionInternal(actionId: String, waitForCompletion: Boolean): ExecutionResult {
    var executionResult: ExecutionResult? = null
    var executionException: Exception? = null
    
    val executeOnEdt = Runnable {
        try {
            // All UI operations inside EDT-safe block
            val project = getActiveProject()
            val dataContext = createComprehensiveDataContext(project)
            
            // Create and execute action event
            val event = AnActionEvent(...)
            ActionUtil.performDumbAwareUpdate(action, event, true)
            ActionUtil.invokeAction(action, dataContext, ActionPlaces.KEYBOARD_SHORTCUT, null, null)
            
            executionResult = ExecutionResult(true, actionId, "Success")
        } catch (e: Exception) {
            executionException = e
        }
    }
    
    // Choose execution method based on requirements
    if (waitForCompletion && !isDialogAction(actionId)) {
        // Synchronous execution for action chains
        if (ApplicationManager.getApplication().isDispatchThread) {
            executeOnEdt.run()  // Already on EDT
        } else {
            ApplicationManager.getApplication().invokeAndWait(executeOnEdt)
        }
    } else {
        // Asynchronous execution for dialogs and single actions
        ApplicationManager.getApplication().invokeLater(executeOnEdt)
        return ExecutionResult(true, actionId, "Triggered successfully")
    }
    
    return executionResult ?: ExecutionResult(false, actionId, error = executionException?.message)
}
```

### Action Type Categorization

#### Dialog Actions (Asynchronous)
Execute with `invokeLater` to prevent blocking:
```kotlin
private fun isDialogAction(actionId: String): Boolean {
    val dialogActions = setOf(
        "About", "ShowSettings", "ShowProjectStructureSettings",
        "CheckinProject", "Git.CompareWithBranch", "Git.Branches",
        "RefactoringMenu", "RenameElement", "Move", "ExtractMethod"
    )
    return dialogActions.contains(actionId)
}
```

#### UI Actions (Synchronous with Delays)
Execute with `invokeAndWait` and add UI update delays:
```kotlin
private fun isUIAction(actionId: String): Boolean {
    val uiActions = setOf(
        "ActivateProjectToolWindow", "ActivateStructureToolWindow",
        "SplitVertically", "SplitHorizontally", "NextSplitter",
        "MaximizeToolWindow", "HideAllWindows", "Tree-selectFirst"
    )
    return uiActions.contains(actionId)
}

// Usage
if (isUIAction(actionId)) {
    Thread.sleep(50)  // Allow UI to update
}
```

#### Regular Actions (Synchronous)
Standard execution for most actions like formatting, navigation, etc.

## DataContext Creation

### Comprehensive Context Strategy
The plugin creates rich DataContext to ensure actions have all required information:

```kotlin
private fun createComprehensiveDataContext(project: Project?): DataContext {
    if (project == null) {
        return SimpleDataContext.EMPTY_CONTEXT
    }
    
    // Gather all possible context elements
    val editor = getCurrentEditor(project)
    val virtualFile = getCurrentVirtualFile(project, editor)
    val psiFile = getPsiFile(project, virtualFile)
    val selectedFiles = getSelectedFiles(project) ?: virtualFile?.let { arrayOf(it) }
    val module = virtualFile?.let { ModuleUtil.findModuleForFile(it, project) }
    
    val builder = SimpleDataContext.builder()
        // Core IntelliJ context
        .add(CommonDataKeys.PROJECT, project)
        .add(CommonDataKeys.EDITOR, editor)
        .add(CommonDataKeys.HOST_EDITOR, editor)  // Some actions check HOST_EDITOR
        .add(CommonDataKeys.VIRTUAL_FILE, virtualFile)
        .add(CommonDataKeys.PSI_FILE, psiFile)
        
    // File arrays - ensure non-null arrays
    if (selectedFiles != null) {
        builder.add(CommonDataKeys.VIRTUAL_FILE_ARRAY, selectedFiles)
    } else if (virtualFile != null) {
        builder.add(CommonDataKeys.VIRTUAL_FILE_ARRAY, arrayOf(virtualFile))
    } else {
        builder.add(CommonDataKeys.VIRTUAL_FILE_ARRAY, emptyArray<VirtualFile>())
    }
    
    // PSI elements for refactoring actions
    if (psiFile != null) {
        builder.add(CommonDataKeys.PSI_ELEMENT, psiFile)
        builder.add(LangDataKeys.PSI_ELEMENT_ARRAY, arrayOf(psiFile))
    }
    
    // Editor-specific context
    editor?.let {
        builder.add(CommonDataKeys.CARET, it.caretModel.currentCaret)
        builder.add(CommonDataKeys.EDITOR_VIRTUAL_SPACE, false)
        builder.add(PlatformDataKeys.CONTEXT_COMPONENT, it.contentComponent)
    }
    
    // Module context for build/run actions
    module?.let {
        builder.add(LangDataKeys.MODULE, it)
        builder.add(LangDataKeys.MODULE_CONTEXT, it)
    }
    
    // Project view selection context
    val selectedItem = getSelectedItem(project)
    val selectedItems = getSelectedItems(project)
    selectedItem?.let { builder.add(PlatformDataKeys.SELECTED_ITEM, it) }
    selectedItems?.let { builder.add(PlatformDataKeys.SELECTED_ITEMS, it) }
    
    // Navigation context for file operations
    val navigatableArray = selectedFiles?.mapNotNull { 
        PsiManager.getInstance(project).findFile(it) 
    }?.toTypedArray()
    navigatableArray?.let { 
        builder.add(CommonDataKeys.NAVIGATABLE_ARRAY, it)
        if (it.isNotEmpty()) {
            builder.add(CommonDataKeys.NAVIGATABLE, it[0])
        }
    }
    
    return builder.build()
}
```

### Context Resolution Strategy

#### 1. Current Editor Resolution
```kotlin
private fun getCurrentEditor(project: Project?): Editor? {
    project ?: return null
    return FileEditorManager.getInstance(project).selectedTextEditor
}
```

#### 2. Virtual File Resolution (Multiple Fallbacks)
```kotlin
private fun getCurrentVirtualFile(project: Project?, editor: Editor?): VirtualFile? {
    project ?: return null
    
    // Try from editor document first
    editor?.document?.let { doc ->
        FileDocumentManager.getInstance().getFile(doc)?.let { return it }
    }
    
    // Try from FileEditorManager
    FileEditorManager.getInstance(project).selectedFiles.firstOrNull()?.let { return it }
    
    // Try from project view selection
    try {
        val projectView = ProjectView.getInstance(project)
        val selectedElements = projectView.currentProjectViewPane?.selectedElements
        selectedElements?.firstOrNull()?.let { element ->
            when (element) {
                is PsiFile -> return element.virtualFile
                is VirtualFile -> return element
            }
        }
    } catch (e: Exception) {
        LOG.debug("Could not get file from project view: ${e.message}")
    }
    
    // Last resort - project base directory
    return project.baseDir
}
```

#### 3. Selected Files Resolution
```kotlin
private fun getSelectedFiles(project: Project?): Array<VirtualFile>? {
    project ?: return null
    
    try {
        val projectView = ProjectView.getInstance(project)
        val pane = projectView.currentProjectViewPane
        
        if (pane != null) {
            val selectedElements = pane.selectedElements
            val files = selectedElements.mapNotNull { element ->
                when (element) {
                    is PsiFile -> element.virtualFile
                    is VirtualFile -> element
                    is PsiFileNode -> element.virtualFile
                    is PsiDirectoryNode -> element.virtualFile
                    else -> null
                }
            }.toTypedArray()
            
            if (files.isNotEmpty()) return files
        }
    } catch (e: Exception) {
        LOG.debug("Error getting selected files: ${e.message}")
    }
    
    // Fallback to current editor file
    val currentFile = getCurrentVirtualFile(project, null)
    return currentFile?.let { arrayOf(it) }
}
```

## Action Execution Patterns

### Ataman-Inspired Approach (v1.1.2+)

#### Problem with Direct Execution
```kotlin
// ❌ INCORRECT - Actions may not work in all contexts
action.actionPerformed(event)
```

#### Solution: ActionUtil Pattern
```kotlin
// ✅ CORRECT - Ataman-inspired execution
val presentation = Presentation()
val event = AnActionEvent(
    null,
    dataContext,
    ActionPlaces.KEYBOARD_SHORTCUT,  // Critical: Always use KEYBOARD_SHORTCUT
    presentation,
    actionManager,
    0
)

// Update action state first
ActionUtil.performDumbAwareUpdate(action, event, true)

// Check if action is enabled
if (!event.presentation.isEnabled) {
    return ExecutionResult(false, actionId, error = "Action disabled in current context")
}

// Execute using ActionUtil
ActionUtil.invokeAction(action, dataContext, ActionPlaces.KEYBOARD_SHORTCUT, null, null)
```

#### Why KEYBOARD_SHORTCUT Place?
- Most consistent action context across IntelliJ
- Actions are designed to work from keyboard triggers
- Provides proper UI focus and selection context
- Matches user expectations for action behavior

### Action State Validation

#### Dumb Mode Awareness
```kotlin
ActionUtil.performDumbAwareUpdate(action, event, true)
```
This ensures actions are properly updated even during:
- Project indexing
- File system scanning
- Background processing

#### Context Validation
```kotlin
if (!event.presentation.isEnabled) {
    LOG.warn("Action is disabled: $actionId")
    LOG.warn("Context details - Has editor: ${dataContext.getData(CommonDataKeys.EDITOR) != null}, " +
            "Has file: ${dataContext.getData(CommonDataKeys.VIRTUAL_FILE) != null}, " +
            "Has file array: ${dataContext.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)?.size ?: 0}")
    return ExecutionResult(false, actionId, error = "Action disabled in current context")
}
```

## Server Architecture

### Dual Server Design

#### Built-in RestService (Port 63342)
```kotlin
class ActionRestService : RestService() {
    override fun getServiceName(): String = "intellij-actions"
    
    override fun isMethodSupported(method: HttpMethod): Boolean {
        return method == HttpMethod.GET || method == HttpMethod.POST
    }
    
    override fun isHostTrusted(request: FullHttpRequest, urlDecoder: QueryStringDecoder): Boolean {
        val host = request.headers()["Host"] ?: return false
        return host.startsWith("localhost") || host.startsWith("127.0.0.1")
    }
}
```

**Advantages:**
- Integrated with IntelliJ's infrastructure
- Automatic lifecycle management
- Built-in security (host validation)

**Disadvantages:**
- Subject to rate limiting (429 errors)
- Less control over threading
- HTML-wrapped JSON responses

#### Custom HttpServer (Port 63343)
```kotlin
class CustomHttpServer(private val port: Int = 63343) {
    private var server: HttpServer? = null
    private val executor = Executors.newFixedThreadPool(10)
    
    fun start() {
        server = HttpServer.create(InetSocketAddress(port), 0)
        server?.apply {
            createContext("/api/intellij-actions/execute", ExecuteHandler())
            createContext("/api/intellij-actions/health", HealthHandler())
            setExecutor(executor)  // 10-thread pool
            start()
        }
    }
}
```

**Advantages:**
- No rate limiting
- Direct JSON responses
- Configurable thread pool
- CORS support for browser automation

**Disadvantages:**
- Manual lifecycle management
- Additional resource overhead
- Custom security implementation needed

### JSON Serialization Strategy

#### Lightweight Custom Implementation
```kotlin
private fun toJson(data: Any?): String {
    return when (data) {
        null -> "null"
        is Boolean -> data.toString()
        is Number -> data.toString()
        is String -> "\"${data.replace("\"", "\\\"")}\""
        is Map<*, *> -> {
            val entries = data.entries.joinToString(",") { (k, v) ->
                "${toJson(k.toString())}:${toJson(v)}"
            }
            "{$entries}"
        }
        is List<*> -> {
            val items = data.joinToString(",") { toJson(it) }
            "[$items]"
        }
        else -> toJson(data.toString())
    }
}
```

**Benefits:**
- No external dependencies
- Avoids library conflicts
- Minimal memory footprint
- Fast for simple objects

## Performance Optimizations

### 1. Thread Pool Management
```kotlin
private val executor = Executors.newFixedThreadPool(10)
```
- Fixed pool prevents thread exhaustion
- Optimal size for typical usage patterns
- Proper shutdown handling

### 2. Lazy Service Loading
```kotlin
@Service
class ActionExecutorService {
    companion object {
        @JvmStatic
        fun getInstance(): ActionExecutorService = 
            ApplicationManager.getApplication().getService(ActionExecutorService::class.java)
    }
}
```
- Services loaded on-demand
- Singleton pattern for shared state
- IntelliJ manages lifecycle

### 3. Smart Delay Strategy
```kotlin
// Base delay between actions
if (delayMs > 0 && index < actionIds.size - 1) {
    Thread.sleep(delayMs)
    
    // Extra delay for UI actions
    if (isUIAction(actionId)) {
        Thread.sleep(100)  // Additional UI settle time
    }
}
```

### 4. Action Manager Caching
IntelliJ's ActionManager caches action instances, so repeated calls are efficient:
```kotlin
val action = actionManager.getAction(actionId)  // Cached lookup
```

## Error Handling Strategy

### 1. Layered Exception Handling
```kotlin
fun executeAction(actionId: String): ExecutionResult {
    return try {
        executeActionInternal(actionId, false)
    } catch (e: Exception) {
        LOG.error("Failed to execute action: $actionId", e)
        ExecutionResult(false, actionId, error = e.message)
    }
}
```

### 2. EDT Exception Isolation
```kotlin
val executeOnEdt = Runnable {
    try {
        // UI operations
    } catch (e: Exception) {
        LOG.error("Error executing action: $actionId", e)
        executionException = e  // Capture for main thread
    }
}
```

### 3. Action Chain Failure Handling
```kotlin
for ((index, actionId) in actionIds.withIndex()) {
    val result = executeActionAndWait(actionId)
    results.add(result)
    
    if (!result.success) {
        LOG.warn("Stopping action chain due to failure at '$actionId': ${result.error}")
        break  // Stop chain on first failure
    }
}
```

### 4. HTTP Error Response Standardization
```kotlin
// Success response
{"success": true, "action": "ActionId", "message": "Success", "error": null}

// Error response  
{"success": false, "action": "ActionId", "message": null, "error": "Error description"}
```

## Known Limitations

### 1. Context Dependencies
Some actions require specific context that may not be available:
```kotlin
// Examples of context-dependent actions:
- RunClass: Needs executable class in context
- Git.Push: Needs VCS-enabled project
- ExtractMethod: Needs code selection
- CopyPaths: Needs file selection (now works in v1.1.2+)
```

### 2. Modal Dialog Interference
Actions that open modal dialogs can block action chains:
```kotlin
// Problematic sequences:
["ShowSettings", "ReformatCode"]  // Settings dialog blocks ReformatCode
```

### 3. UI Focus Requirements
Some actions require IntelliJ to have focus:
```kotlin
// Focus-dependent actions:
- Editor navigation actions
- Tool window activation
- Menu-based actions
```

### 4. Project State Dependencies
```kotlin
// Actions that may fail based on project state:
- Build actions during indexing
- VCS actions without repository
- Run actions without configurations
```

### 5. Thread Timing Issues
Even with EDT safety, complex UI updates may require additional delays:
```kotlin
// Actions needing extra settle time:
- SplitVertically (UI layout changes)
- ActivateProjectToolWindow (focus changes)
- Tree-selectFirst (tree expansion)
```

## Future Enhancement Opportunities

### 1. WebSocket Support
Real-time bidirectional communication for:
- Action progress updates
- IDE state notifications
- Interactive action sequences

### 2. Context Persistence
Remember and reuse successful contexts:
```kotlin
// Concept: Context cache
private val contextCache = mutableMapOf<String, DataContext>()

fun getCachedContext(actionId: String): DataContext? {
    return contextCache[actionId]
}
```

### 3. Action Macros
Save and replay action sequences:
```kotlin
// Concept: Macro recording
data class ActionMacro(
    val name: String,
    val actions: List<String>,
    val delays: List<Long>
)
```

### 4. Enhanced Error Recovery
Automatic retry with different contexts:
```kotlin
// Concept: Fallback contexts
fun executeWithFallback(actionId: String): ExecutionResult {
    val contexts = listOf(
        createComprehensiveDataContext(project),
        createMinimalDataContext(project),
        SimpleDataContext.EMPTY_CONTEXT
    )
    
    for (context in contexts) {
        val result = tryExecute(actionId, context)
        if (result.success) return result
    }
    
    return ExecutionResult(false, actionId, error = "All contexts failed")
}
```