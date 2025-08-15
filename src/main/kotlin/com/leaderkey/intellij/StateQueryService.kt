package com.leaderkey.intellij

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ide.DataManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.PsiTreeUtil

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
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(toolWindowId)
        return toolWindow?.isActive == true
    }
    
    fun isToolWindowVisible(toolWindowId: String): Boolean {
        val project = getActiveProject() ?: return false
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(toolWindowId)
        return toolWindow?.isVisible == true
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
        return FileEditorManager.getInstance(project).selectedEditor != null
    }
    
    fun hasOpenFile(): Boolean {
        val project = getActiveProject() ?: return false
        val fileEditor = FileEditorManager.getInstance(project)
        return fileEditor.selectedFiles.isNotEmpty()
    }
    
    fun isProjectViewVisible(): Boolean {
        return isToolWindowVisible("Project")
    }
    
    fun isTerminalOpen(): Boolean {
        return isToolWindowVisible("Terminal")
    }
    
    fun queryState(stateId: String): Boolean {
        val project = getActiveProject()
        
        return when {
            stateId == "editor" || stateId == "hasEditor" -> hasOpenEditor()
            stateId == "file" || stateId == "hasFile" -> hasOpenFile()
            stateId == "project" || stateId == "hasProject" -> project != null
            stateId == "projectView" -> isProjectViewVisible()
            stateId == "terminal" -> isTerminalOpen()
            
            // Document state conditions
            stateId == "hasModifications" || stateId == "hasUnsavedChanges" -> {
                FileDocumentManager.getInstance().unsavedDocuments.isNotEmpty()
            }
            stateId == "hasErrors" -> {
                project?.let { proj ->
                    val psiFile = getCurrentPsiFile(proj)
                    psiFile?.let { PsiTreeUtil.hasErrorElements(it) } ?: false
                } ?: false
            }
            stateId == "hasSelection" -> {
                project?.let { proj ->
                    val editor = FileEditorManager.getInstance(proj).selectedTextEditor
                    editor?.selectionModel?.hasSelection() ?: false
                } ?: false
            }
            
            // IDE state conditions
            stateId == "isIndexing" || stateId == "indexing" -> {
                project?.let { DumbService.getInstance(it).isDumb } ?: false
            }
            
            // VCS conditions (basic check - Git plugin might not be available)
            stateId == "gitRepository" || stateId == "hasGit" -> {
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
            stateId.startsWith("fileType:") -> {
                val type = stateId.removePrefix("fileType:")
                project?.let { hasFileType(it, type) } ?: false
            }
            stateId.startsWith("hasExtension:") -> {
                val ext = stateId.removePrefix("hasExtension:")
                project?.let { hasFileExtension(it, ext) } ?: false
            }
            
            // Existing conditions
            stateId.endsWith(":enabled") -> {
                val actionId = stateId.removeSuffix(":enabled")
                isActionEnabled(actionId)
            }
            stateId.endsWith(":window") || stateId.endsWith(":toolWindow") -> {
                val windowId = stateId.removeSuffix(":window").removeSuffix(":toolWindow")
                isToolWindowVisible(windowId)
            }
            stateId.endsWith(":active") -> {
                val windowId = stateId.removeSuffix(":active")
                isToolWindowActive(windowId)
            }
            else -> {
                LOG.warn("Unknown state query: $stateId")
                false
            }
        }
    }
    
    fun queryStates(checks: List<String>): Map<String, Boolean> {
        val results = mutableMapOf<String, Boolean>()
        checks.forEach { check ->
            results[check] = queryState(check)
        }
        return results
    }
    
    fun canExecuteActions(actionIds: List<String>): Map<String, Boolean> {
        val results = mutableMapOf<String, Boolean>()
        actionIds.forEach { actionId ->
            results[actionId] = isActionEnabled(actionId)
        }
        return results
    }
    
    private fun getActiveProject(): Project? {
        val projects = ProjectManager.getInstance().openProjects
        if (projects.isEmpty()) return null
        
        // Try to get the project with focus
        for (project in projects) {
            val windowManager = ToolWindowManager.getInstance(project)
            if (windowManager.isEditorComponentActive) {
                return project
            }
        }
        
        // Fallback to first open project
        return projects.firstOrNull()
    }
    
    private fun getLiveDataContext(project: Project): DataContext {
        // Try to get context from focused component
        val focusOwner = IdeFocusManager.getInstance(project).focusOwner
        if (focusOwner != null) {
            return DataManager.getInstance().getDataContext(focusOwner)
        }
        
        // Fallback to editor context
        val editor = FileEditorManager.getInstance(project).selectedTextEditor
        if (editor != null) {
            return DataManager.getInstance().getDataContext(editor.component)
        }
        
        // Fallback to project context
        return SimpleDataContext.builder()
            .add(CommonDataKeys.PROJECT, project)
            .build()
    }
    
    private fun getCurrentPsiFile(project: Project): PsiFile? {
        val editor = FileEditorManager.getInstance(project).selectedTextEditor
        val document = editor?.document ?: return null
        return PsiDocumentManager.getInstance(project).getPsiFile(document)
    }
    
    private fun getCurrentVirtualFile(project: Project): VirtualFile? {
        return FileEditorManager.getInstance(project).selectedFiles.firstOrNull()
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
}