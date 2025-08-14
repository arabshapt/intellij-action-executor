package com.leaderkey.intellij

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
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
        return try {
            LOG.info("Starting execution of action: $actionId")
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
            
            // Create a simple data context
            val dataContext = SimpleDataContext.builder()
                .add(CommonDataKeys.PROJECT, project)
                .build()
            
            val presentation = Presentation()
            val event = AnActionEvent(
                null,
                dataContext,
                ActionPlaces.UNKNOWN,
                presentation,
                actionManager,
                0
            )
            
            LOG.info("Created AnActionEvent")
            
            // Update and check if enabled
            action.update(event)
            LOG.info("Action enabled: ${event.presentation.isEnabled}")
            
            if (!event.presentation.isEnabled) {
                LOG.warn("Action is disabled: $actionId")
                return ExecutionResult(false, actionId, error = "Action is disabled in current context")
            }
            
            // Execute the action asynchronously - don't wait for completion
            LOG.info("About to perform action: $actionId")
            
            // Always use invokeLater to avoid blocking on dialog actions
            ApplicationManager.getApplication().invokeLater {
                try {
                    LOG.info("Executing action on EDT: $actionId")
                    action.actionPerformed(event)
                    LOG.info("Action completed: $actionId")
                } catch (e: Exception) {
                    LOG.error("Error in action execution: $actionId", e)
                }
            }
            
            LOG.info("Returning success immediately for: $actionId")
            ExecutionResult(true, actionId, "Action triggered successfully")
        } catch (e: Exception) {
            LOG.error("Failed to execute action: $actionId", e)
            ExecutionResult(false, actionId, error = e.message)
        }
    }
    
    fun executeActions(actionIds: List<String>, delayMs: Long = 100): List<ExecutionResult> {
        val results = mutableListOf<ExecutionResult>()
        
        for (actionId in actionIds) {
            val result = executeAction(actionId)
            results.add(result)
            
            if (!result.success) {
                LOG.warn("Stopping action chain due to failure: ${result.error}")
                break
            }
            
            if (delayMs > 0 && actionId != actionIds.last()) {
                Thread.sleep(delayMs)
            }
        }
        
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
}