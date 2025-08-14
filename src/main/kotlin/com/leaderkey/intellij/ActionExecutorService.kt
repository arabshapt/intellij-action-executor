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
            
            LOG.info("About to perform action: $actionId")
            
            if (waitForCompletion && !isDialogAction(actionId)) {
                // For non-dialog actions in a sequence, wait for completion
                var executionException: Exception? = null
                
                if (ApplicationManager.getApplication().isDispatchThread) {
                    // If we're already on EDT, just execute directly
                    try {
                        LOG.info("Executing action directly on EDT: $actionId")
                        action.actionPerformed(event)
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
                            action.actionPerformed(event)
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
                        action.actionPerformed(event)
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
}