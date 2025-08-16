package com.leaderkey.intellij

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ide.DataManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.PsiTreeUtil
import java.awt.Component
import javax.swing.SwingUtilities

@Service
class StateQueryService {
    companion object {
        private val LOG = Logger.getInstance(StateQueryService::class.java)
        
        @JvmStatic
        fun getInstance(): StateQueryService = 
            ApplicationManager.getApplication().getService(StateQueryService::class.java)
    }
    
    fun isToolWindowActive(toolWindowId: String): Boolean {
        val project = getActiveProject() ?: return false
        var result = false
        ApplicationManager.getApplication().invokeAndWait({
            val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(toolWindowId)
            result = toolWindow?.isActive == true
        }, ModalityState.any())
        return result
    }
    
    fun isToolWindowVisible(toolWindowId: String): Boolean {
        val project = getActiveProject() ?: return false
        var result = false
        ApplicationManager.getApplication().invokeAndWait({
            val toolWindowManager = ToolWindowManager.getInstance(project)
            val toolWindow = toolWindowManager.getToolWindow(toolWindowId)
            result = toolWindow?.isVisible == true
        }, ModalityState.any())
        return result
    }
    
    fun isActionEnabled(actionId: String): Boolean {
        val actionManager = ActionManager.getInstance()
        val action = actionManager.getAction(actionId) ?: return false
        
        val project = getActiveProject()
        val dataContext = if (project != null) {
            getLiveDataContext(project)
        } else {
            SimpleDataContext.EMPTY_CONTEXT
        }
        
        val presentation = Presentation()
        val event = AnActionEvent(
            null,
            dataContext,
            ActionPlaces.KEYBOARD_SHORTCUT,
            presentation,
            actionManager,
            0
        )
        
        ActionUtil.performDumbAwareUpdate(action, event, true)
        return event.presentation.isEnabled
    }
    
    fun hasOpenEditor(): Boolean {
        val project = getActiveProject() ?: return false
        return ApplicationManager.getApplication().runReadAction<Boolean> {
            FileEditorManager.getInstance(project).selectedEditor != null
        }
    }
    
    fun hasOpenFile(): Boolean {
        val project = getActiveProject() ?: return false
        return ApplicationManager.getApplication().runReadAction<Boolean> {
            val fileEditor = FileEditorManager.getInstance(project)
            fileEditor.selectedFiles.isNotEmpty()
        }
    }
    
    fun isProjectViewVisible(): Boolean {
        return isToolWindowVisible("Project")
    }
    
    fun isTerminalOpen(): Boolean {
        return isToolWindowVisible("Terminal")
    }
    
    fun queryState(stateId: String): Boolean {
        // Don't wrap in runReadAction - let individual methods handle their threading
        return queryStateInternal(stateId)
    }
    
    private fun queryStateInternal(stateId: String): Boolean {
        // Check for negation operators
        val isNegated = stateId.startsWith("!") || stateId.startsWith("not:")
        val actualStateId = when {
            stateId.startsWith("!") -> stateId.substring(1)
            stateId.startsWith("not:") -> stateId.substring(4)
            else -> stateId
        }
        
        val project = getActiveProject()
        
        val result = when {
            actualStateId == "editor" || actualStateId == "hasEditor" -> hasOpenEditor()
            actualStateId == "file" || actualStateId == "hasFile" -> hasOpenFile()
            actualStateId == "project" || actualStateId == "hasProject" -> project != null
            actualStateId == "projectView" -> isProjectViewVisible()
            actualStateId == "terminal" -> isTerminalOpen()
            
            // Document state conditions
            actualStateId == "hasModifications" || actualStateId == "hasUnsavedChanges" -> {
                ApplicationManager.getApplication().runReadAction<Boolean> {
                    FileDocumentManager.getInstance().unsavedDocuments.isNotEmpty()
                }
            }
            actualStateId == "hasErrors" -> {
                project?.let { proj ->
                    val psiFile = getCurrentPsiFile(proj)
                    ApplicationManager.getApplication().runReadAction<Boolean> {
                        psiFile?.let { PsiTreeUtil.hasErrorElements(it) } ?: false
                    }
                } ?: false
            }
            actualStateId == "hasSelection" -> {
                project?.let { proj ->
                    ApplicationManager.getApplication().runReadAction<Boolean> {
                        val editor = FileEditorManager.getInstance(proj).selectedTextEditor
                        editor?.selectionModel?.hasSelection() ?: false
                    }
                } ?: false
            }
            
            // IDE state conditions
            actualStateId == "isIndexing" || actualStateId == "indexing" -> {
                project?.let { proj ->
                    ApplicationManager.getApplication().runReadAction<Boolean> {
                        DumbService.getInstance(proj).isDumb
                    }
                } ?: false
            }
            
            // Focus conditions
            actualStateId == "focusInEditor" || actualStateId == "editorHasFocus" -> {
                project?.let { isEditorFocused(it) } ?: false
            }
            actualStateId == "focusInProject" -> {
                project?.let { getFocusedToolWindow(it) == "Project" } ?: false
            }
            actualStateId == "focusInTerminal" -> {
                project?.let { getFocusedToolWindow(it) == "Terminal" } ?: false
            }
            actualStateId == "focusInToolWindow" -> {
                project?.let { getFocusedToolWindow(it) != null } ?: false
            }
            actualStateId == "hasFocus" || actualStateId == "ideFocused" -> {
                isIDEFocused(project)
            }
            
            // VCS conditions (basic check - Git plugin might not be available)
            actualStateId == "gitRepository" || actualStateId == "hasGit" -> {
                project?.let { proj ->
                    try {
                        // Check if .git directory exists in project root
                        val projectDir = proj.basePath?.let { java.io.File(it, ".git") }
                        projectDir?.exists() ?: false
                    } catch (e: Exception) {
                        false
                    }
                } ?: false
            }
            
            // Parameterized conditions
            actualStateId.startsWith("fileType:") -> {
                val type = actualStateId.removePrefix("fileType:")
                project?.let { hasFileType(it, type) } ?: false
            }
            actualStateId.startsWith("hasExtension:") -> {
                val ext = actualStateId.removePrefix("hasExtension:")
                project?.let { hasFileExtension(it, ext) } ?: false
            }
            actualStateId.startsWith("focusInToolWindow:") -> {
                val toolWindowId = actualStateId.removePrefix("focusInToolWindow:")
                project?.let { getFocusedToolWindow(it) == toolWindowId } ?: false
            }
            actualStateId.startsWith("focusInFile:") -> {
                val fileName = actualStateId.removePrefix("focusInFile:")
                project?.let { getFocusedFileName(it) == fileName } ?: false
            }
            
            // Existing conditions
            actualStateId.endsWith(":enabled") -> {
                val actionId = actualStateId.removeSuffix(":enabled")
                isActionEnabled(actionId)
            }
            actualStateId.endsWith(":window") || actualStateId.endsWith(":toolWindow") -> {
                val windowId = actualStateId.removeSuffix(":window").removeSuffix(":toolWindow")
                isToolWindowVisible(windowId)
            }
            actualStateId.endsWith(":active") -> {
                val windowId = actualStateId.removeSuffix(":active")
                isToolWindowActive(windowId)
            }
            else -> {
                LOG.warn("Unknown state query: $actualStateId")
                false
            }
        }
        
        // Apply negation if needed
        return if (isNegated) !result else result
    }
    
    fun queryStates(checks: List<String>): Map<String, Boolean> {
        // Don't wrap in runReadAction - let individual methods handle their threading
        val results = mutableMapOf<String, Boolean>()
        checks.forEach { check ->
            results[check] = queryStateInternal(check)
        }
        return results
    }
    
    fun getAllToolWindows(): Map<String, Map<String, Boolean>> {
        val project = getActiveProject() ?: return emptyMap()
        val results = mutableMapOf<String, Map<String, Boolean>>()
        
        ApplicationManager.getApplication().invokeAndWait({
            val toolWindowManager = ToolWindowManager.getInstance(project)
            toolWindowManager.toolWindowIds.forEach { id ->
                val toolWindow = toolWindowManager.getToolWindow(id)
                if (toolWindow != null) {
                    results[id] = mapOf(
                        "visible" to toolWindow.isVisible,
                        "active" to toolWindow.isActive,
                        "available" to toolWindow.isAvailable
                    )
                }
            }
        }, ModalityState.any())
        
        return results
    }
    
    fun canExecuteActions(actionIds: List<String>): Map<String, Boolean> {
        return ApplicationManager.getApplication().runReadAction<Map<String, Boolean>> {
            val results = mutableMapOf<String, Boolean>()
            actionIds.forEach { actionId ->
                results[actionId] = isActionEnabled(actionId)
            }
            results
        }
    }
    
    private fun getActiveProject(): Project? {
        val projects = ProjectManager.getInstance().openProjects
        if (projects.isEmpty()) return null
        
        // If we're already on EDT, don't use invokeAndWait
        if (ApplicationManager.getApplication().isDispatchThread) {
            // Try to get the project with focus
            for (project in projects) {
                val windowManager = ToolWindowManager.getInstance(project)
                if (windowManager.isEditorComponentActive) {
                    return project
                }
            }
            return projects.firstOrNull()
        }
        
        // Otherwise, use invokeAndWait to check on EDT
        var result: Project? = null
        ApplicationManager.getApplication().invokeAndWait({
            // Try to get the project with focus
            for (project in projects) {
                val windowManager = ToolWindowManager.getInstance(project)
                if (windowManager.isEditorComponentActive) {
                    result = project
                    return@invokeAndWait
                }
            }
            // Fallback to first open project
            result = projects.firstOrNull()
        }, ModalityState.any())
        return result
    }
    
    private fun getLiveDataContext(project: Project): DataContext {
        // If we're already on EDT, get context directly
        if (ApplicationManager.getApplication().isDispatchThread) {
            val focusOwner = IdeFocusManager.getInstance(project).focusOwner
            if (focusOwner != null) {
                return DataManager.getInstance().getDataContext(focusOwner)
            }
            
            val editor = FileEditorManager.getInstance(project).selectedTextEditor
            if (editor != null) {
                return DataManager.getInstance().getDataContext(editor.component)
            }
            
            return SimpleDataContext.builder()
                .add(CommonDataKeys.PROJECT, project)
                .build()
        }
        
        // Otherwise, get context on EDT
        var result: DataContext? = null
        ApplicationManager.getApplication().invokeAndWait({
            val focusOwner = IdeFocusManager.getInstance(project).focusOwner
            if (focusOwner != null) {
                result = DataManager.getInstance().getDataContext(focusOwner)
                return@invokeAndWait
            }
            
            val editor = FileEditorManager.getInstance(project).selectedTextEditor
            if (editor != null) {
                result = DataManager.getInstance().getDataContext(editor.component)
                return@invokeAndWait
            }
            
            result = SimpleDataContext.builder()
                .add(CommonDataKeys.PROJECT, project)
                .build()
        }, ModalityState.any())
        return result!!
    }
    
    private fun getCurrentPsiFile(project: Project): PsiFile? {
        return ApplicationManager.getApplication().runReadAction<PsiFile?> {
            val editor = FileEditorManager.getInstance(project).selectedTextEditor
            val document = editor?.document ?: return@runReadAction null
            PsiDocumentManager.getInstance(project).getPsiFile(document)
        }
    }
    
    private fun getCurrentVirtualFile(project: Project): VirtualFile? {
        return ApplicationManager.getApplication().runReadAction<VirtualFile?> {
            FileEditorManager.getInstance(project).selectedFiles.firstOrNull()
        }
    }
    
    private fun hasFileType(project: Project, type: String): Boolean {
        val file = getCurrentVirtualFile(project) ?: return false
        val fileType = file.fileType
        return fileType.name.equals(type, ignoreCase = true) || 
               fileType.defaultExtension.equals(type, ignoreCase = true)
    }
    
    private fun hasFileExtension(project: Project, extension: String): Boolean {
        val file = getCurrentVirtualFile(project) ?: return false
        return file.extension?.equals(extension, ignoreCase = true) == true
    }
    
    private fun getFocusedToolWindow(project: Project): String? {
        var result: String? = null
        ApplicationManager.getApplication().invokeAndWait({
            val toolWindowManager = ToolWindowManager.getInstance(project)
            val focusOwner = IdeFocusManager.getInstance(project).focusOwner
            if (focusOwner != null) {
                // Check each tool window to see if it contains the focused component
                for (toolWindowId in toolWindowManager.toolWindowIds) {
                    val toolWindow = toolWindowManager.getToolWindow(toolWindowId) ?: continue
                    val toolWindowComponent = toolWindow.component
                    if (isComponentAncestor(toolWindowComponent, focusOwner)) {
                        result = toolWindowId
                        return@invokeAndWait
                    }
                }
            }
        }, ModalityState.any())
        return result
    }
    
    private fun isEditorFocused(project: Project): Boolean {
        var result = false
        ApplicationManager.getApplication().invokeAndWait({
            val focusOwner = IdeFocusManager.getInstance(project).focusOwner
            val editor = FileEditorManager.getInstance(project).selectedTextEditor
            if (focusOwner != null && editor != null) {
                result = isComponentAncestor(editor.component, focusOwner)
            }
        }, ModalityState.any())
        return result
    }
    
    private fun getFocusedFileName(project: Project): String? {
        if (!isEditorFocused(project)) return null
        return getCurrentVirtualFile(project)?.name
    }
    
    private fun isComponentAncestor(ancestor: Component, descendant: Component): Boolean {
        return ancestor == descendant || SwingUtilities.isDescendingFrom(descendant, ancestor)
    }
    
    private fun isIDEFocused(project: Project?): Boolean {
        if (project == null) return false
        var result = false
        ApplicationManager.getApplication().invokeAndWait({
            val frame = WindowManager.getInstance().getFrame(project)
            result = frame?.isFocused ?: false
        }, ModalityState.any())
        return result
    }
}