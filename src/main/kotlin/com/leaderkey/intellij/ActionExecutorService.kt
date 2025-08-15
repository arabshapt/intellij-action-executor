package com.leaderkey.intellij

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ide.projectView.ProjectView
import com.intellij.ide.projectView.impl.nodes.PsiFileNode
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode
import com.intellij.ide.DataManager
import com.intellij.openapi.wm.IdeFocusManager
import javax.swing.SwingUtilities

@Service
class ActionExecutorService {
    companion object {
        private val LOG = Logger.getInstance(ActionExecutorService::class.java)
        
        @JvmStatic
        fun getInstance(): ActionExecutorService = 
            ApplicationManager.getApplication().getService(ActionExecutorService::class.java)
    }

    enum class ErrorType {
        ACTION_NOT_FOUND,
        MISSING_CONTEXT,
        ACTION_DISABLED,
        PROJECT_REQUIRED,
        EDITOR_REQUIRED,
        FILE_REQUIRED,
        GIT_REPOSITORY_REQUIRED,
        BUILD_SYSTEM_REQUIRED,
        UNKNOWN_ERROR
    }
    
    data class ExecutionResult(
        val success: Boolean,
        val actionId: String,
        val message: String? = null,
        val error: String? = null,
        val errorType: ErrorType? = null,
        val suggestion: String? = null,
        val requiredContext: List<String>? = null
    )

    fun executeAction(actionId: String): ExecutionResult {
        return executeActionInternal(actionId, waitForCompletion = false)
    }
    
    private fun executeActionAndWait(actionId: String): ExecutionResult {
        return executeActionInternal(actionId, waitForCompletion = true)
    }
    
    private fun executeActionInternal(actionId: String, waitForCompletion: Boolean): ExecutionResult {
        return try {
            LOG.info("Starting execution of action: $actionId (wait=$waitForCompletion)")
            LOG.info("Current thread: ${Thread.currentThread().name}")
            LOG.info("Is EDT: ${SwingUtilities.isEventDispatchThread()}")
            
            val actionManager = ActionManager.getInstance()
            val action = actionManager.getAction(actionId)
            
            if (action == null) {
                LOG.warn("Action not found: $actionId")
                return ExecutionResult(
                    success = false,
                    actionId = actionId,
                    error = "Action not found: $actionId",
                    errorType = ErrorType.ACTION_NOT_FOUND,
                    suggestion = "Check available actions with 'ij --list' or search with 'ij --search ${actionId.take(5)}'"
                )
            }
            
            LOG.info("Action found: ${action.javaClass.name}")
            
            // Execute everything on EDT to avoid threading issues
            var executionResult: ExecutionResult? = null
            var executionException: Exception? = null
            
            val executeOnEdt = Runnable {
                try {
                    // Get project and create data context on EDT
                    val project = getActiveProject()
                    LOG.info("Active project: ${project?.name ?: "null"}")
                    
                    // Get live data context from focused component or fallback to synthetic
                    val dataContext = getLiveDataContext(project)
                    
                    // Use ataman's approach - always use KEYBOARD_SHORTCUT
                    val presentation = Presentation()
                    val event = AnActionEvent(
                        null,
                        dataContext,
                        ActionPlaces.KEYBOARD_SHORTCUT,
                        presentation,
                        actionManager,
                        0
                    )
                    
                    LOG.info("Created AnActionEvent with KEYBOARD_SHORTCUT place")
                    
                    // Use ActionUtil to properly update and check if enabled
                    ActionUtil.performDumbAwareUpdate(action, event, true)
                    LOG.info("Action enabled after update: ${event.presentation.isEnabled}")
                    
                    if (!event.presentation.isEnabled) {
                        LOG.warn("Action is disabled: $actionId")
                        val hasEditor = dataContext.getData(CommonDataKeys.EDITOR) != null
                        val hasFile = dataContext.getData(CommonDataKeys.VIRTUAL_FILE) != null
                        val hasFileArray = dataContext.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)?.isNotEmpty() == true
                        val hasPsi = dataContext.getData(CommonDataKeys.PSI_FILE) != null
                        val hasProject = dataContext.getData(CommonDataKeys.PROJECT) != null
                        
                        LOG.warn("Data context details - Has editor: $hasEditor, Has file: $hasFile, " +
                                "Has file array: $hasFileArray, Has PSI: $hasPsi, Has project: $hasProject")
                        
                        val (errorType, suggestion, requiredContext) = analyzeDisabledAction(
                            actionId, hasProject, hasEditor, hasFile, hasFileArray
                        )
                        
                        executionResult = ExecutionResult(
                            success = false,
                            actionId = actionId,
                            error = "Action is disabled in current context",
                            errorType = errorType,
                            suggestion = suggestion,
                            requiredContext = requiredContext
                        )
                        return@Runnable
                    }
                    
                    LOG.info("About to perform action: $actionId")
                    ActionUtil.invokeAction(action, dataContext, ActionPlaces.KEYBOARD_SHORTCUT, null, null)
                    LOG.info("Action completed: $actionId")
                    
                    executionResult = ExecutionResult(true, actionId, "Action triggered successfully")
                } catch (e: Exception) {
                    LOG.error("Error executing action: $actionId", e)
                    executionException = e
                }
            }
            
            // Execute on EDT based on wait preference
            if (waitForCompletion && !isDialogAction(actionId)) {
                // For non-dialog actions in a sequence, wait for completion
                if (ApplicationManager.getApplication().isDispatchThread) {
                    executeOnEdt.run()
                } else {
                    ApplicationManager.getApplication().invokeAndWait(executeOnEdt)
                }
            } else {
                // For dialog actions or single executions, use async
                ApplicationManager.getApplication().invokeLater(executeOnEdt)
                // Return success immediately for async actions
                return ExecutionResult(true, actionId, "Action triggered successfully")
            }
            
            if (executionException != null) {
                return ExecutionResult(
                    success = false,
                    actionId = actionId,
                    error = executionException?.message,
                    errorType = ErrorType.UNKNOWN_ERROR,
                    suggestion = "Check IntelliJ logs for details. Try restarting IntelliJ if the problem persists."
                )
            }
            
            return executionResult ?: ExecutionResult(
                success = false,
                actionId = actionId,
                error = "Unknown error",
                errorType = ErrorType.UNKNOWN_ERROR,
                suggestion = "The action failed for an unknown reason. Check IntelliJ logs for details."
            )
        } catch (e: Exception) {
            LOG.error("Failed to execute action: $actionId", e)
            ExecutionResult(
                success = false,
                actionId = actionId,
                error = e.message,
                errorType = ErrorType.UNKNOWN_ERROR,
                suggestion = "An unexpected error occurred. Check IntelliJ logs for details."
            )
        }
    }
    
    private fun isDialogAction(actionId: String): Boolean {
        // Delegate to ActionCategorizer for consistency
        return ActionCategorizer.isDialogAction(actionId)
    }
    
    private fun isUIAction(actionId: String): Boolean {
        // Delegate to ActionCategorizer for consistency
        return ActionCategorizer.isUIAction(actionId)
    }
    
    fun executeActions(actionIds: List<String>, delayMs: Long = -1): List<ExecutionResult> {
        val results = mutableListOf<ExecutionResult>()
        val useSmartDelay = delayMs < 0  // -1 means use smart delays
        
        for ((index, actionId) in actionIds.withIndex()) {
            LOG.info("Executing action ${index + 1}/${actionIds.size}: $actionId")
            
            // Use synchronous execution for all actions in a chain to ensure proper sequencing
            val result = executeActionAndWait(actionId)
            results.add(result)
            
            if (!result.success) {
                LOG.warn("Stopping action chain due to failure at action '$actionId': ${result.error}")
                break
            }
            
            // Add delay between actions if not the last action
            if (index < actionIds.size - 1) {
                val nextAction = actionIds[index + 1]
                val delay = if (useSmartDelay) {
                    ActionCategorizer.getOptimalDelay(actionId, nextAction)
                } else {
                    delayMs
                }
                
                if (delay > 0) {
                    LOG.info("Delay ${delay}ms after '$actionId' before '$nextAction'")
                    Thread.sleep(delay)
                } else {
                    LOG.debug("No delay needed between '$actionId' and '$nextAction'")
                }
            }
        }
        
        LOG.info("Action chain completed. ${results.count { it.success }}/${actionIds.size} succeeded")
        return results
    }
    
    private fun getActiveProject(): Project? {
        // Try to get the most recently focused project
        val projectManager = ProjectManager.getInstance()
        val openProjects = projectManager.openProjects
        
        if (openProjects.isEmpty()) {
            return null
        }
        
        // Return the first open project
        return openProjects.firstOrNull()
    }
    
    
    private fun createAnActionEvent(
        action: AnAction,
        dataContext: DataContext
    ): AnActionEvent {
        val presentation = action.templatePresentation.clone()
        val actionManager = ActionManager.getInstance()
        val place = ActionPlaces.UNKNOWN
        
        return AnActionEvent(
            null,
            dataContext,
            place,
            presentation,
            actionManager,
            0
        )
    }
    
    fun isActionAvailable(actionId: String): Boolean {
        val actionManager = ActionManager.getInstance()
        return actionManager.getAction(actionId) != null
    }
    
    fun getAllActionIds(): List<String> {
        val actionManager = ActionManager.getInstance()
        return actionManager.getActionIds("").toList()
    }
    
    private fun createComprehensiveDataContext(project: Project?): DataContext {
        if (project == null) {
            LOG.info("No project available, returning empty context")
            return SimpleDataContext.EMPTY_CONTEXT
        }
        
        // Get all possible context elements
        val editor = getCurrentEditor(project)
        val virtualFile = getCurrentVirtualFile(project, editor)
        val psiFile = getPsiFile(project, virtualFile)
        val selectedFiles = getSelectedFiles(project) ?: virtualFile?.let { arrayOf(it) }
        val module = virtualFile?.let { ModuleUtil.findModuleForFile(it, project) }
        val toolWindowId = ToolWindowManager.getInstance(project).activeToolWindowId
        val projectView = ProjectView.getInstance(project)
        val projectViewPane = projectView.currentProjectViewPane
        
        LOG.info("Creating comprehensive context - Editor: ${editor != null}, File: ${virtualFile?.name}, " +
                "PSI: ${psiFile != null}, Selected: ${selectedFiles?.size ?: 0} files")
        
        val builder = SimpleDataContext.builder()
            // Core context
            .add(CommonDataKeys.PROJECT, project)
            .add(CommonDataKeys.EDITOR, editor)
            .add(CommonDataKeys.HOST_EDITOR, editor) // Some actions check for HOST_EDITOR
            .add(CommonDataKeys.VIRTUAL_FILE, virtualFile)
            .add(CommonDataKeys.PSI_FILE, psiFile)
            
        // Add arrays if not null - ensure we always have an array even if empty
        if (selectedFiles != null) {
            builder.add(CommonDataKeys.VIRTUAL_FILE_ARRAY, selectedFiles)
        } else if (virtualFile != null) {
            // If no selected files but we have a current file, use it as the array
            builder.add(CommonDataKeys.VIRTUAL_FILE_ARRAY, arrayOf(virtualFile))
        } else {
            // Provide empty array to avoid null checks in actions
            builder.add(CommonDataKeys.VIRTUAL_FILE_ARRAY, emptyArray<VirtualFile>())
        }
        
        // Add PSI elements
        if (psiFile != null) {
            builder.add(CommonDataKeys.PSI_ELEMENT, psiFile)
            builder.add(LangDataKeys.PSI_ELEMENT_ARRAY, arrayOf(psiFile))
        }
        
        // Editor context
        editor?.let {
            builder.add(CommonDataKeys.CARET, it.caretModel.currentCaret)
            builder.add(CommonDataKeys.EDITOR_VIRTUAL_SPACE, false)
            builder.add(PlatformDataKeys.CONTEXT_COMPONENT, it.contentComponent)
        }
        
        // Module context
        module?.let {
            builder.add(LangDataKeys.MODULE, it)
            builder.add(LangDataKeys.MODULE_CONTEXT, it)
        }
        
        // Selected items from project view
        val selectedItem = getSelectedItem(project)
        val selectedItems = getSelectedItems(project)
        selectedItem?.let { builder.add(PlatformDataKeys.SELECTED_ITEM, it) }
        selectedItems?.let { builder.add(PlatformDataKeys.SELECTED_ITEMS, it) }
        
        // Navigation context - use PSI files as navigatables
        val navigatableArray = selectedFiles?.mapNotNull { 
            PsiManager.getInstance(project).findFile(it) 
        }?.toTypedArray()
        navigatableArray?.let { 
            builder.add(CommonDataKeys.NAVIGATABLE_ARRAY, it)
            // Also add the first navigatable as single item
            if (it.isNotEmpty()) {
                builder.add(CommonDataKeys.NAVIGATABLE, it[0])
            }
        }
        
        return builder.build()
    }
    
    private fun getLiveDataContext(project: Project?): DataContext {
        if (project == null) {
            LOG.info("No project available, returning empty context")
            return SimpleDataContext.EMPTY_CONTEXT
        }
        
        // Try to get context from focused component
        val focusOwner = IdeFocusManager.getInstance(project).focusOwner
        if (focusOwner != null) {
            LOG.info("Got live context from focused component: ${focusOwner.javaClass.simpleName}")
            return DataManager.getInstance().getDataContext(focusOwner)
        }
        
        // Try async focus context
        try {
            val asyncContext = DataManager.getInstance().getDataContextFromFocusAsync()
                .blockingGet(100)
            if (asyncContext != null) {
                LOG.info("Got live context from async focus")
                return asyncContext
            }
        } catch (e: Exception) {
            LOG.debug("Could not get async focus context: ${e.message}")
        }
        
        // Fallback to synthetic context (for headless/testing)
        LOG.info("No focused component found, using synthetic context as fallback")
        return createComprehensiveDataContext(project)
    }
    
    private fun getCurrentEditor(project: Project?): Editor? {
        project ?: return null
        return FileEditorManager.getInstance(project).selectedTextEditor
    }
    
    private fun getCurrentVirtualFile(project: Project?, editor: Editor?): VirtualFile? {
        project ?: return null
        
        // Try from editor first
        editor?.document?.let { doc ->
            FileDocumentManager.getInstance().getFile(doc)?.let { 
                LOG.info("Got virtual file from editor: ${it.name}")
                return it 
            }
        }
        
        // Then try from FileEditorManager
        FileEditorManager.getInstance(project).selectedFiles.firstOrNull()?.let {
            LOG.info("Got virtual file from FileEditorManager: ${it.name}")
            return it
        }
        
        // Try to get from project view selection
        try {
            val projectView = ProjectView.getInstance(project)
            val selectedElements = projectView.currentProjectViewPane?.selectedElements
            selectedElements?.firstOrNull()?.let { element ->
                when (element) {
                    is PsiFile -> element.virtualFile?.let {
                        LOG.info("Got virtual file from project view: ${it.name}")
                        return it
                    }
                    is VirtualFile -> {
                        LOG.info("Got virtual file directly from project view: ${element.name}")
                        return element
                    }
                    else -> {
                        LOG.debug("Selected element is not a file: ${element?.javaClass?.simpleName}")
                    }
                }
            }
        } catch (e: Exception) {
            LOG.debug("Could not get file from project view: ${e.message}")
        }
        
        // Last resort - try to get project base directory
        project.baseDir?.let {
            LOG.info("Using project base directory as fallback: ${it.name}")
            return it
        }
        
        LOG.info("No virtual file found")
        return null
    }
    
    private fun getPsiFile(project: Project?, virtualFile: VirtualFile?): PsiFile? {
        project ?: return null
        virtualFile ?: return null
        return PsiManager.getInstance(project).findFile(virtualFile)
    }
    
    private fun getSelectedFiles(project: Project?): Array<VirtualFile>? {
        project ?: return null
        
        try {
            // Try to get selected files from project view
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
                
                if (files.isNotEmpty()) {
                    LOG.info("Got ${files.size} selected files from project view")
                    return files
                }
            }
        } catch (e: Exception) {
            LOG.debug("Error getting selected files from project view: ${e.message}")
        }
        
        // Fallback to current editor file
        val currentFile = getCurrentVirtualFile(project, null)
        return currentFile?.let { 
            LOG.info("Using current file as selected: ${it.name}")
            arrayOf(it) 
        }
    }
    
    private fun analyzeDisabledAction(
        actionId: String,
        hasProject: Boolean,
        hasEditor: Boolean,
        hasFile: Boolean,
        hasFileArray: Boolean
    ): Triple<ErrorType, String, List<String>> {
        val requiredContext = mutableListOf<String>()
        
        // Check project requirement
        if (!hasProject) {
            requiredContext.add("Project")
            return Triple(
                ErrorType.PROJECT_REQUIRED,
                "No project is open. Open a project in IntelliJ first.",
                requiredContext
            )
        }
        
        // Analyze based on action patterns
        when {
            // Editor-related actions
            actionId.contains("Reformat") || actionId.contains("Optimize") || 
            actionId.contains("Comment") || actionId.startsWith("Editor") -> {
                if (!hasEditor) {
                    requiredContext.add("Editor")
                    return Triple(
                        ErrorType.EDITOR_REQUIRED,
                        "Open a file in the editor first, then try again.",
                        requiredContext
                    )
                }
            }
            
            // File operations
            actionId.contains("Copy") || actionId.contains("Move") || 
            actionId.contains("Delete") || actionId.contains("Rename") -> {
                if (!hasFile && !hasFileArray) {
                    requiredContext.add("File selection")
                    return Triple(
                        ErrorType.FILE_REQUIRED,
                        "Select a file in the Project view or open one in the editor.",
                        requiredContext
                    )
                }
            }
            
            // VCS operations
            actionId.startsWith("Git.") || actionId.startsWith("Vcs.") || 
            actionId.contains("Commit") || actionId.contains("Push") || actionId.contains("Pull") -> {
                requiredContext.add("Git repository")
                return Triple(
                    ErrorType.GIT_REPOSITORY_REQUIRED,
                    "Action requires a Git repository. Ensure your project is under version control.",
                    requiredContext
                )
            }
            
            // Build operations
            actionId.contains("Build") || actionId.contains("Compile") || 
            actionId.contains("Make") || actionId.contains("Run") || actionId.contains("Debug") -> {
                requiredContext.add("Build configuration")
                return Triple(
                    ErrorType.BUILD_SYSTEM_REQUIRED,
                    "Configure your build system (Maven/Gradle) or run configuration first.",
                    requiredContext
                )
            }
            
            // Tree/List operations
            actionId.startsWith("Tree-") || actionId.startsWith("List-") -> {
                requiredContext.add("Tree/List focus")
                return Triple(
                    ErrorType.MISSING_CONTEXT,
                    "Focus on a tree or list component first (e.g., Project view, Structure view).",
                    requiredContext
                )
            }
        }
        
        // Default case - generic context issue
        requiredContext.add("Appropriate context")
        return Triple(
            ErrorType.MISSING_CONTEXT,
            "Action '$actionId' requires specific context. Try: 1) Open a file, 2) Select items in Project view, 3) Focus on the appropriate tool window.",
            requiredContext
        )
    }
    
    private fun getSelectedItem(project: Project?): Any? {
        project ?: return null
        try {
            val projectView = ProjectView.getInstance(project)
            return projectView.currentProjectViewPane?.selectedElements?.firstOrNull()
        } catch (e: Exception) {
            LOG.debug("Error getting selected item: ${e.message}")
            return null
        }
    }
    
    private fun getSelectedItems(project: Project?): Array<Any>? {
        project ?: return null
        try {
            val projectView = ProjectView.getInstance(project)
            return projectView.currentProjectViewPane?.selectedElements
        } catch (e: Exception) {
            LOG.debug("Error getting selected items: ${e.message}")
            return null
        }
    }
}