package com.leaderkey.intellij

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.WindowManager
import java.awt.Component
import javax.swing.SwingUtilities

@Service
class FocusManagementService {
    companion object {
        private val LOG = Logger.getInstance(FocusManagementService::class.java)
        
        @JvmStatic
        fun getInstance(): FocusManagementService = 
            ApplicationManager.getApplication().getService(FocusManagementService::class.java)
    }
    
    data class FocusState(
        val hasFocus: Boolean,
        val focusInEditor: Boolean,
        val focusInTerminal: Boolean,
        val focusInToolWindow: Boolean,
        val activeToolWindow: String?,
        val focusedComponentClass: String?
    )
    
    fun getCurrentFocusState(project: Project): FocusState {
        val focusManager = IdeFocusManager.getInstance(project)
        val toolWindowManager = ToolWindowManager.getInstance(project)
        val focusOwner = focusManager.focusOwner
        
        val hasFocus = isIDEFocused(project)
        val focusInEditor = isEditorFocused(project, focusOwner)
        val focusInTerminal = isTerminalFocused(project, focusOwner)
        val focusInToolWindow = isAnyToolWindowFocused(project, focusOwner)
        val activeToolWindow = toolWindowManager.activeToolWindowId
        val focusedComponentClass = focusOwner?.javaClass?.simpleName
        
        return FocusState(
            hasFocus = hasFocus,
            focusInEditor = focusInEditor,
            focusInTerminal = focusInTerminal,
            focusInToolWindow = focusInToolWindow,
            activeToolWindow = activeToolWindow,
            focusedComponentClass = focusedComponentClass
        )
    }
    
    fun focusEditor(project: Project, forceActivate: Boolean = false): Boolean {
        val editor = FileEditorManager.getInstance(project).selectedTextEditor
        if (editor == null) {
            LOG.warn("No editor available to focus")
            return false
        }
        
        return requestFocusInEDT(project, editor.contentComponent, "editor")
    }
    
    fun focusTerminal(project: Project): Boolean {
        val toolWindowManager = ToolWindowManager.getInstance(project)
        val terminalWindow = toolWindowManager.getToolWindow("Terminal")
        
        if (terminalWindow == null) {
            LOG.warn("Terminal tool window not found")
            return false
        }
        
        // First make sure terminal is visible
        if (!terminalWindow.isVisible) {
            terminalWindow.show(null)
            Thread.sleep(100) // Give it time to show
        }
        
        // Then activate it
        terminalWindow.activate {
            LOG.info("Terminal activated via callback")
        }
        
        return true
    }
    
    fun focusToolWindow(project: Project, toolWindowId: String): Boolean {
        val toolWindowManager = ToolWindowManager.getInstance(project)
        val toolWindow = toolWindowManager.getToolWindow(toolWindowId)
        
        if (toolWindow == null) {
            LOG.warn("Tool window not found: $toolWindowId")
            return false
        }
        
        // First make sure it's visible
        if (!toolWindow.isVisible) {
            toolWindow.show(null)
            Thread.sleep(100) // Give it time to show
        }
        
        // Then activate it
        toolWindow.activate {
            LOG.info("Tool window $toolWindowId activated via callback")
        }
        
        return true
    }
    
    fun switchFocusFromTerminalToEditor(project: Project): Boolean {
        val focusState = getCurrentFocusState(project)
        
        if (!focusState.focusInTerminal) {
            LOG.info("Focus is not in terminal, cannot switch from terminal to editor")
            // Try to focus editor anyway
            return focusEditor(project)
        }
        
        // Hide terminal first to ensure clean transition
        val toolWindowManager = ToolWindowManager.getInstance(project)
        val terminalWindow = toolWindowManager.getToolWindow("Terminal")
        terminalWindow?.hide {
            // After hiding, focus editor
            ApplicationManager.getApplication().invokeLater {
                focusEditor(project, true)
            }
        }
        
        return true
    }
    
    fun toggleBetweenEditorAndLastToolWindow(project: Project): Boolean {
        val focusState = getCurrentFocusState(project)
        val toolWindowManager = ToolWindowManager.getInstance(project)
        
        if (focusState.focusInEditor) {
            // Currently in editor, switch to last tool window
            val lastToolWindow = toolWindowManager.lastActiveToolWindowId 
                ?: toolWindowManager.activeToolWindowId
                ?: "Project" // Default to project view
            
            return focusToolWindow(project, lastToolWindow)
        } else {
            // Currently in tool window, switch to editor
            return focusEditor(project, true)
        }
    }
    
    private fun requestFocusInEDT(project: Project, component: Component, componentName: String): Boolean {
        var success = false
        
        if (SwingUtilities.isEventDispatchThread()) {
            val focusManager = IdeFocusManager.getInstance(project)
            focusManager.requestFocus(component, true)
            success = true
            LOG.info("Focus requested for $componentName on EDT")
        } else {
            ApplicationManager.getApplication().invokeAndWait {
                val focusManager = IdeFocusManager.getInstance(project)
                focusManager.requestFocus(component, true)
                success = true
                LOG.info("Focus requested for $componentName via invokeAndWait")
            }
        }
        
        return success
    }
    
    private fun isIDEFocused(project: Project): Boolean {
        val frame = WindowManager.getInstance().getFrame(project)
        return frame?.isFocused ?: false
    }
    
    private fun isEditorFocused(project: Project, focusOwner: Component?): Boolean {
        if (focusOwner == null) return false
        
        val editor = FileEditorManager.getInstance(project).selectedTextEditor
        return editor != null && SwingUtilities.isDescendingFrom(focusOwner, editor.component)
    }
    
    private fun isTerminalFocused(project: Project, focusOwner: Component?): Boolean {
        if (focusOwner == null) return false
        
        val toolWindowManager = ToolWindowManager.getInstance(project)
        val terminalWindow = toolWindowManager.getToolWindow("Terminal") ?: return false
        
        return SwingUtilities.isDescendingFrom(focusOwner, terminalWindow.component)
    }
    
    private fun isAnyToolWindowFocused(project: Project, focusOwner: Component?): Boolean {
        if (focusOwner == null) return false
        
        val toolWindowManager = ToolWindowManager.getInstance(project)
        
        for (toolWindowId in toolWindowManager.toolWindowIds) {
            val toolWindow = toolWindowManager.getToolWindow(toolWindowId) ?: continue
            if (SwingUtilities.isDescendingFrom(focusOwner, toolWindow.component)) {
                return true
            }
        }
        
        return false
    }
    
    fun logFocusState(project: Project) {
        val state = getCurrentFocusState(project)
        LOG.info("""
            Focus State:
            - IDE has focus: ${state.hasFocus}
            - Focus in editor: ${state.focusInEditor}
            - Focus in terminal: ${state.focusInTerminal}
            - Focus in tool window: ${state.focusInToolWindow}
            - Active tool window: ${state.activeToolWindow ?: "none"}
            - Focused component: ${state.focusedComponentClass ?: "none"}
        """.trimIndent())
    }
}