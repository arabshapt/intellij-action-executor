package com.leaderkey.intellij

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger

@Service
class ConditionalExecutor {
    companion object {
        private val LOG = Logger.getInstance(ConditionalExecutor::class.java)
        
        @JvmStatic
        fun getInstance(): ConditionalExecutor = 
            ApplicationManager.getApplication().getService(ConditionalExecutor::class.java)
    }
    
    sealed class Condition {
        data class IfThenElse(
            val check: String,  // e.g., "editor", "Git.Pull:enabled", "Terminal:window"
            val thenChain: List<String>,
            val elseChain: List<String>? = null
        ) : Condition()
        
        data class OrChain(
            val chains: List<List<String>>  // Try each chain until one succeeds
        ) : Condition()
    }
    
    data class ConditionalResult(
        val success: Boolean,
        val executedActions: List<String>,
        val message: String? = null,
        val error: String? = null,
        val conditionMet: Boolean? = null
    )
    
    fun executeConditional(
        condition: Condition, 
        forceMode: Boolean = false
    ): ConditionalResult {
        LOG.info("Executing conditional: $condition (force=$forceMode)")
        
        return when (condition) {
            is Condition.IfThenElse -> executeIfThenElse(condition, forceMode)
            is Condition.OrChain -> executeOrChain(condition, forceMode)
        }
    }
    
    private fun executeIfThenElse(
        condition: Condition.IfThenElse,
        forceMode: Boolean
    ): ConditionalResult {
        val stateService = StateQueryService.getInstance()
        val checkResult = stateService.queryState(condition.check)
        
        LOG.info("Condition check '${condition.check}' = $checkResult")
        
        return if (checkResult) {
            val result = executeChain(condition.thenChain, forceMode)
            ConditionalResult(
                success = result.success,
                executedActions = result.executedActions,
                message = "Condition met, executed then branch",
                conditionMet = true
            )
        } else {
            if (condition.elseChain != null) {
                val result = executeChain(condition.elseChain, forceMode)
                ConditionalResult(
                    success = result.success,
                    executedActions = result.executedActions,
                    message = "Condition not met, executed else branch",
                    conditionMet = false
                )
            } else {
                ConditionalResult(
                    success = true,
                    executedActions = emptyList(),
                    message = "Condition not met, no else branch",
                    conditionMet = false
                )
            }
        }
    }
    
    private fun executeOrChain(
        condition: Condition.OrChain,
        forceMode: Boolean
    ): ConditionalResult {
        val allExecutedActions = mutableListOf<String>()
        
        for ((index, chain) in condition.chains.withIndex()) {
            LOG.info("Trying OR chain ${index + 1}/${condition.chains.size}: $chain")
            
            val result = executeChain(chain, forceMode)
            allExecutedActions.addAll(result.executedActions)
            
            if (result.success) {
                return ConditionalResult(
                    success = true,
                    executedActions = allExecutedActions,
                    message = "OR chain succeeded at branch ${index + 1}"
                )
            }
        }
        
        return ConditionalResult(
            success = false,
            executedActions = allExecutedActions,
            error = "All OR chains failed"
        )
    }
    
    private fun executeChain(
        actions: List<String>,
        forceMode: Boolean
    ): ChainResult {
        val actionService = ActionExecutorService.getInstance()
        val executedActions = mutableListOf<String>()
        var allSuccess = true
        
        for (action in actions) {
            LOG.info("Executing action in chain: $action")
            
            val result = actionService.executeAction(action)
            executedActions.add(action)
            
            if (!result.success) {
                allSuccess = false
                if (!forceMode) {
                    LOG.info("Action failed, stopping chain (force=$forceMode): $action")
                    break
                } else {
                    LOG.info("Action failed but continuing (force=$forceMode): $action")
                }
            }
        }
        
        return ChainResult(
            success = allSuccess || (forceMode && executedActions.isNotEmpty()),
            executedActions = executedActions
        )
    }
    
    fun parseOrChains(input: String): Condition.OrChain {
        // Parse pipe-separated chains
        val chains = input.split("|").map { chainStr ->
            chainStr.trim().split(",").map { it.trim() }
        }
        return Condition.OrChain(chains)
    }
    
    fun parseConditionalFromArgs(
        check: String?,
        thenActions: String?,
        elseActions: String?
    ): Condition? {
        if (check == null || thenActions == null) {
            return null
        }
        
        // Parse then actions (may contain pipes for OR)
        val thenParts = thenActions.split("|").map { it.trim() }
        
        return if (thenParts.size > 1) {
            // Then branch has OR chains
            val thenChains = thenParts.map { part ->
                part.split(",").map { it.trim() }
            }
            
            // For now, we'll execute the first successful then-chain
            // This is a simplification - could be enhanced
            Condition.IfThenElse(
                check = check,
                thenChain = thenChains.first(),
                elseChain = elseActions?.split(",")?.map { it.trim() }
            )
        } else {
            // Simple then branch
            Condition.IfThenElse(
                check = check,
                thenChain = thenActions.split(",").map { it.trim() },
                elseChain = elseActions?.split(",")?.map { it.trim() }
            )
        }
    }
    
    private data class ChainResult(
        val success: Boolean,
        val executedActions: List<String>
    )
}