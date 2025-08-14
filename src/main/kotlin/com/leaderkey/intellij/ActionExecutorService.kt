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
import javax.swing.SwingUtilities

@Service
class ActionExecutorService {
    companion object {
        private val LOG = Logger.getInstance(ActionExecutorService::class.java)
        
        @JvmStatic
        fun getInstance(): ActionExecutorService = 
            ApplicationManager.getApplication().getService(ActionExecutorService::class.java)
    }

    data class ExecutionResult(
        val success: Boolean,
        val actionId: String,
        val message: String? = null,
        val error: String? = null
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
                return ExecutionResult(false, actionId, error = "Action not found: $actionId")
            }
            
            LOG.info("Action found: ${action.javaClass.name}")
            
            // Try the simplest approach - just execute it
            val project = getActiveProject()
            LOG.info("Active project: ${project?.name ?: "null"}")
            
            // Create comprehensive data context with all available information
            val dataContext = createComprehensiveDataContext(project)
            
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
                LOG.warn("Data context details - Has editor: ${dataContext.getData(CommonDataKeys.EDITOR) != null}, " +
                        "Has file: ${dataContext.getData(CommonDataKeys.VIRTUAL_FILE) != null}, " +
                        "Has file array: ${dataContext.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)?.size ?: 0}, " +
                        "Has PSI: ${dataContext.getData(CommonDataKeys.PSI_FILE) != null}")
                return ExecutionResult(false, actionId, error = "Action is disabled in current context")
            }
            
            LOG.info("About to perform action: $actionId")
            
            if (waitForCompletion && !isDialogAction(actionId)) {
                // For non-dialog actions in a sequence, wait for completion
                var executionException: Exception? = null
                
                if (ApplicationManager.getApplication().isDispatchThread) {
                    // If we're already on EDT, just execute directly
                    try {
                        LOG.info("Executing action directly on EDT: $actionId")
                        ActionUtil.invokeAction(action, dataContext, ActionPlaces.KEYBOARD_SHORTCUT, null, null)
                        LOG.info("Action completed directly: $actionId")
                    } catch (e: Exception) {
                        LOG.error("Error in direct action execution: $actionId", e)
                        executionException = e
                    }
                } else {
                    // If not on EDT, use invokeAndWait
                    ApplicationManager.getApplication().invokeAndWait {
                        try {
                            LOG.info("Executing action synchronously via invokeAndWait: $actionId")
                            ActionUtil.invokeAction(action, dataContext, ActionPlaces.KEYBOARD_SHORTCUT, null, null)
                            LOG.info("Action completed synchronously: $actionId")
                        } catch (e: Exception) {
                            LOG.error("Error in synchronous action execution: $actionId", e)
                            executionException = e
                        }
                    }
                }
                
                if (executionException != null) {
                    return ExecutionResult(false, actionId, error = executionException?.message)
                }
                
                // For UI actions, give a small delay for UI to update
                if (isUIAction(actionId)) {
                    Thread.sleep(50)
                }
            } else {
                // For dialog actions or single executions, use async
                ApplicationManager.getApplication().invokeLater {
                    try {
                        LOG.info("Executing action asynchronously on EDT: $actionId")
                        ActionUtil.invokeAction(action, dataContext, ActionPlaces.KEYBOARD_SHORTCUT, null, null)
                        LOG.info("Action completed asynchronously: $actionId")
                    } catch (e: Exception) {
                        LOG.error("Error in asynchronous action execution: $actionId", e)
                    }
                }
            }
            
            LOG.info("Returning success for: $actionId")
            ExecutionResult(true, actionId, "Action triggered successfully")
        } catch (e: Exception) {
            LOG.error("Failed to execute action: $actionId", e)
            ExecutionResult(false, actionId, error = e.message)
        }
    }
    
    private fun isDialogAction(actionId: String): Boolean {
        // Actions that typically open dialogs and should not block
        val dialogActions = setOf(
            "About", "ShowSettings", "ShowProjectStructureSettings",
            "CheckinProject", "Git.CompareWithBranch", "Git.Branches",
            "RefactoringMenu", "RenameElement", "Move", "ExtractMethod"
        )
        return dialogActions.contains(actionId)
    }
    
    private fun isUIAction(actionId: String): Boolean {
        // Actions that manipulate UI and need time to update
        val uiActions = setOf(
            "ActivateProjectToolWindow", "ActivateStructureToolWindow",
            "ActivateFavoritesToolWindow", "ActivateVersionControlToolWindow",
            "SplitVertically", "SplitHorizontally", "NextSplitter",
            "MaximizeToolWindow", "HideAllWindows", "Tree-selectFirst"
        )
        return uiActions.contains(actionId)
    }
    
    fun executeActions(actionIds: List<String>, delayMs: Long = 250): List<ExecutionResult> {
        val results = mutableListOf<ExecutionResult>()
        
        for ((index, actionId) in actionIds.withIndex()) {
            LOG.info("Executing action ${index + 1}/${actionIds.size}: $actionId")
            
            // Use synchronous execution for all actions in a chain to ensure proper sequencing
            val result = executeActionAndWait(actionId)
            results.add(result)
            
            if (!result.success) {
                LOG.warn("Stopping action chain due to failure at action '$actionId': ${result.error}")
                break
            }
            
            // Add delay between actions (increased default to 250ms for better UI responsiveness)
            if (delayMs > 0 && index < actionIds.size - 1) {
                LOG.info("Waiting ${delayMs}ms before next action")
                Thread.sleep(delayMs)
                
                // For UI actions, check if the next action needs more time
                if (isUIAction(actionId)) {
                    // Give extra time for UI actions to fully complete
                    Thread.sleep(100)
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