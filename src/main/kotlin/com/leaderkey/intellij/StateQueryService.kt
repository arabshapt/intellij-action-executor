package com.leaderkey.intellij

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ide.DataManager

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
        return when {
            stateId == "editor" || stateId == "hasEditor" -> hasOpenEditor()
            stateId == "file" || stateId == "hasFile" -> hasOpenFile()
            stateId == "project" || stateId == "hasProject" -> getActiveProject() != null
            stateId == "projectView" -> isProjectViewVisible()
            stateId == "terminal" -> isTerminalOpen()
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
}