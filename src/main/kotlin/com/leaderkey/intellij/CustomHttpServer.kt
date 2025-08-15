package com.leaderkey.intellij

import com.intellij.openapi.diagnostic.Logger
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.util.concurrent.Executors

class CustomHttpServer(private val port: Int = 63343) {
    
    private var server: HttpServer? = null
    private val executor = Executors.newFixedThreadPool(10)
    
    fun start() {
        try {
            server = HttpServer.create(InetSocketAddress(port), 0)
            server?.apply {
                createContext("/api/intellij-actions/execute", ExecuteHandler())
                createContext("/api/intellij-actions/health", HealthHandler())
                createContext("/api/intellij-actions/list", ListHandler())
                createContext("/api/intellij-actions/check", CheckHandler())
                createContext("/api/intellij-actions/search", SearchHandler())
                createContext("/api/intellij-actions/explain", ExplainHandler())
                createContext("/api/intellij-actions/history", HistoryHandler())
                createContext("/api/intellij-actions/stats", StatsHandler())
                createContext("/api/intellij-actions/suggestions", SuggestionsHandler())
                createContext("/api/intellij-actions/state/query", StateHandler())
                createContext("/api/intellij-actions/execute/conditional", ConditionalHandler())
                setExecutor(executor)
                start()
                LOG.info("Custom HTTP server started on port $port")
            }
        } catch (e: Exception) {
            LOG.error("Failed to start custom HTTP server", e)
        }
    }
    
    fun stop() {
        server?.stop(0)
        executor.shutdown()
        LOG.info("Custom HTTP server stopped")
    }
    
    private class ExecuteHandler : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            try {
                val query = exchange.requestURI.query
                val params = CustomHttpServer.parseQuery(query)
                
                val actionService = ActionExecutorService.getInstance()
                
                val response = when {
                    params.containsKey("action") -> {
                        val result = actionService.executeAction(params["action"]!!)
                        CustomHttpServer.toJson(result)
                    }
                    params.containsKey("actions") -> {
                        val actions = params["actions"]!!.split(",")
                        val delay = params["delay"]?.toLongOrNull() ?: -1  // -1 means use smart delays
                        val results = actionService.executeActions(actions, delay)
                        CustomHttpServer.toJsonArray(results)
                    }
                    else -> {
                        """{"success":false,"error":"No action or actions parameter"}"""
                    }
                }
                
                CustomHttpServer.sendResponse(exchange, 200, response)
            } catch (e: Exception) {
                LOG.error("Error handling execute request", e)
                val error = """{"success":false,"error":"${e.message}"}"""
                CustomHttpServer.sendResponse(exchange, 500, error)
            }
        }
    }
    
    private class HealthHandler : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            val response = """{"status":"healthy","plugin":"intellij-action-executor","version":"1.2.0","server":"custom","port":63343}"""
            CustomHttpServer.sendResponse(exchange, 200, response)
        }
    }
    
    private class ListHandler : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            try {
                val actionService = ActionExecutorService.getInstance()
                val actions = actionService.getAllActionIds()
                val actionsJson = actions.joinToString(",") { "\"$it\"" }
                val response = """{"actions":[$actionsJson],"count":${actions.size}}"""
                CustomHttpServer.sendResponse(exchange, 200, response)
            } catch (e: Exception) {
                val error = """{"success":false,"error":"${e.message}"}"""
                CustomHttpServer.sendResponse(exchange, 500, error)
            }
        }
    }
    
    private class CheckHandler : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            try {
                val query = exchange.requestURI.query
                val params = CustomHttpServer.parseQuery(query)
                val actionId = params["action"]
                
                if (actionId == null) {
                    val error = """{"success":false,"error":"No action parameter"}"""
                    CustomHttpServer.sendResponse(exchange, 400, error)
                    return
                }
                
                val actionService = ActionExecutorService.getInstance()
                val exists = actionService.isActionAvailable(actionId)
                val response = """{"exists":$exists,"actionId":"$actionId","available":$exists}"""
                CustomHttpServer.sendResponse(exchange, 200, response)
            } catch (e: Exception) {
                val error = """{"success":false,"error":"${e.message}"}"""
                CustomHttpServer.sendResponse(exchange, 500, error)
            }
        }
    }
    
    private class SearchHandler : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            try {
                val query = exchange.requestURI.query
                val params = CustomHttpServer.parseQuery(query)
                val searchQuery = params["q"] ?: ""
                
                val actionService = ActionExecutorService.getInstance()
                val allActions = actionService.getAllActionIds()
                
                // Filter actions based on search query
                val matchingActions = if (searchQuery.isNotEmpty()) {
                    allActions.filter { actionId ->
                        actionId.contains(searchQuery, ignoreCase = true) ||
                        actionId.split(".", "_", "-").any { 
                            it.startsWith(searchQuery, ignoreCase = true)
                        }
                    }
                } else {
                    emptyList()
                }
                
                val actionsJson = matchingActions.take(100).joinToString(",") { "\"$it\"" }
                val response = """{"query":"$searchQuery","actions":[$actionsJson],"count":${matchingActions.size},"truncated":${matchingActions.size > 100}}"""
                CustomHttpServer.sendResponse(exchange, 200, response)
            } catch (e: Exception) {
                val error = """{"success":false,"error":"${e.message}"}"""
                CustomHttpServer.sendResponse(exchange, 500, error)
            }
        }
    }
    
    private class ExplainHandler : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            try {
                val query = exchange.requestURI.query
                val params = CustomHttpServer.parseQuery(query)
                val actionId = params["action"] ?: ""
                
                if (actionId.isEmpty()) {
                    val error = """{"success":false,"error":"No action parameter provided"}"""
                    CustomHttpServer.sendResponse(exchange, 400, error)
                    return
                }
                
                val actionService = ActionExecutorService.getInstance()
                val exists = actionService.isActionAvailable(actionId)
                
                if (!exists) {
                    val error = """{"success":false,"error":"Action not found: $actionId"}"""
                    CustomHttpServer.sendResponse(exchange, 404, error)
                    return
                }
                
                // Analyze action requirements
                val requirements = analyzeActionRequirements(actionId)
                val category = getActionCategory(actionId)
                val delay = ActionCategorizer.getOptimalDelay(actionId, null)
                
                val response = """{
                    "actionId":"$actionId",
                    "exists":true,
                    "category":"$category",
                    "smartDelay":$delay,
                    "requirements":${requirements.toJson()},
                    "description":"${getActionDescription(actionId)}"
                }"""
                
                CustomHttpServer.sendResponse(exchange, 200, response)
            } catch (e: Exception) {
                val error = """{"success":false,"error":"${e.message}"}"""
                CustomHttpServer.sendResponse(exchange, 500, error)
            }
        }
        
        private fun analyzeActionRequirements(actionId: String): ActionRequirements {
            return ActionRequirements(
                needsEditor = actionId.contains("Reformat") || actionId.contains("Optimize") || 
                             actionId.contains("Comment") || actionId.startsWith("Editor"),
                needsFile = actionId.contains("Copy") || actionId.contains("Move") || 
                           actionId.contains("Delete") || actionId.contains("Rename"),
                needsProject = !actionId.equals("About") && !actionId.equals("ShowSettings"),
                needsGit = actionId.startsWith("Git.") || actionId.startsWith("Vcs."),
                needsBuildSystem = actionId.contains("Build") || actionId.contains("Compile") || 
                                  actionId.contains("Make") || actionId.contains("Run")
            )
        }
        
        private fun getActionCategory(actionId: String): String {
            return when {
                ActionCategorizer.isInstantAction(actionId) -> "instant"
                ActionCategorizer.isQuickUIAction(actionId) -> "quick-ui"
                ActionCategorizer.isToolWindowAction(actionId) -> "tool-window"
                ActionCategorizer.isTreeOrListAction(actionId) -> "tree-list"
                ActionCategorizer.triggersAsyncOperation(actionId) -> "async"
                ActionCategorizer.isDialogAction(actionId) -> "dialog"
                else -> "unknown"
            }
        }
        
        private fun getActionDescription(actionId: String): String {
            // Basic descriptions based on patterns
            return when {
                actionId == "SaveAll" -> "Save all modified files"
                actionId == "ReformatCode" -> "Reformat current file according to code style"
                actionId == "OptimizeImports" -> "Remove unused imports and organize them"
                actionId.startsWith("Git.") -> "Git version control operation"
                actionId.startsWith("Run") -> "Run or execute operation"
                actionId.startsWith("Debug") -> "Debug operation"
                actionId.contains("Copy") -> "Copy operation"
                actionId.contains("Paste") -> "Paste operation"
                actionId.startsWith("Activate") && actionId.endsWith("ToolWindow") -> "Activate tool window"
                actionId.startsWith("Tree-") -> "Tree navigation action"
                else -> "IntelliJ IDEA action"
            }
        }
        
        data class ActionRequirements(
            val needsEditor: Boolean,
            val needsFile: Boolean,
            val needsProject: Boolean,
            val needsGit: Boolean,
            val needsBuildSystem: Boolean
        ) {
            fun toJson(): String {
                return """{
                    "needsEditor":$needsEditor,
                    "needsFile":$needsFile,
                    "needsProject":$needsProject,
                    "needsGit":$needsGit,
                    "needsBuildSystem":$needsBuildSystem
                }"""
            }
        }
    }
    
    private class HistoryHandler : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            try {
                val query = exchange.requestURI.query
                val params = CustomHttpServer.parseQuery(query)
                val limit = params["limit"]?.toIntOrNull() ?: 50
                
                val historyService = ActionHistoryService.getInstance()
                val history = historyService.getRecentHistory(limit)
                
                val historyJson = history.joinToString(",") { entry ->
                    """{"actionId":"${entry.actionId}","timestamp":"${entry.timestamp}","success":${entry.success},"executionTimeMs":${entry.executionTimeMs},"errorType":${entry.errorType?.let { "\"$it\"" } ?: "null"},"chainedWith":${entry.chainedWith?.let { "[${it.joinToString(",") { action -> "\"$action\"" }}]" } ?: "null"}}"""
                }
                
                val response = """{"history":[$historyJson],"count":${history.size}}"""
                CustomHttpServer.sendResponse(exchange, 200, response)
            } catch (e: Exception) {
                val error = """{"success":false,"error":"${e.message}"}"""
                CustomHttpServer.sendResponse(exchange, 500, error)
            }
        }
    }
    
    private class StatsHandler : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            try {
                val query = exchange.requestURI.query
                val params = CustomHttpServer.parseQuery(query)
                val actionId = params["action"]
                
                val historyService = ActionHistoryService.getInstance()
                
                val response = if (actionId != null) {
                    // Get stats for specific action
                    val stats = historyService.getActionStats(actionId)
                    if (stats != null) {
                        """{"actionId":"${stats.actionId}","executionCount":${stats.executionCount},"successCount":${stats.successCount},"failureCount":${stats.failureCount},"averageTimeMs":${stats.averageTimeMs},"lastUsed":"${stats.lastUsed}","commonlyChainedWith":${stats.commonlyChainedWith.entries.joinToString(",", "{", "}") { "\"${it.key}\":${it.value}" }}}"""
                    } else {
                        """{"error":"No statistics available for action: $actionId"}"""
                    }
                } else {
                    // Get top actions
                    val limit = params["limit"]?.toIntOrNull() ?: 20
                    val topActions = historyService.getTopActions(limit)
                    val statsJson = topActions.joinToString(",") { stats ->
                        """{"actionId":"${stats.actionId}","executionCount":${stats.executionCount},"successCount":${stats.successCount},"failureCount":${stats.failureCount},"averageTimeMs":${stats.averageTimeMs},"lastUsed":"${stats.lastUsed}"}"""
                    }
                    """{"topActions":[$statsJson],"count":${topActions.size}}"""
                }
                
                CustomHttpServer.sendResponse(exchange, 200, response)
            } catch (e: Exception) {
                val error = """{"success":false,"error":"${e.message}"}"""
                CustomHttpServer.sendResponse(exchange, 500, error)
            }
        }
    }
    
    private class SuggestionsHandler : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            try {
                val historyService = ActionHistoryService.getInstance()
                val suggestions = historyService.getSuggestions()
                
                val patternsJson = historyService.getCommonPatterns().take(5).joinToString(",") { pattern ->
                    val sequenceJson = pattern.sequence.joinToString(",") { "\"$it\"" }
                    """{"sequence":[$sequenceJson],"frequency":${pattern.frequency},"averageTimeMs":${pattern.averageTimeMs},"suggestion":${pattern.suggestion?.let { "\"$it\"" } ?: "null"}}"""
                }
                
                val suggestionsJson = suggestions.joinToString(",") { "\"$it\"" }
                
                val response = """{"suggestions":[$suggestionsJson],"patterns":[$patternsJson]}"""
                CustomHttpServer.sendResponse(exchange, 200, response)
            } catch (e: Exception) {
                val error = """{"success":false,"error":"${e.message}"}"""
                CustomHttpServer.sendResponse(exchange, 500, error)
            }
        }
    }
    
    private class StateHandler : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            try {
                val query = exchange.requestURI.query
                val params = CustomHttpServer.parseQuery(query)
                val checksParam = params["checks"] ?: ""
                
                if (checksParam.isEmpty()) {
                    val error = """{"success":false,"error":"No checks parameter provided"}"""
                    CustomHttpServer.sendResponse(exchange, 400, error)
                    return
                }
                
                val checks = checksParam.split(",").map { it.trim() }
                val stateService = StateQueryService.getInstance()
                val results = stateService.queryStates(checks)
                
                val resultsJson = results.entries.joinToString(",") { (key, value) ->
                    "\"$key\":$value"
                }
                
                val response = """{$resultsJson}"""
                CustomHttpServer.sendResponse(exchange, 200, response)
            } catch (e: Exception) {
                val error = """{"success":false,"error":"${e.message}"}"""
                CustomHttpServer.sendResponse(exchange, 500, error)
            }
        }
    }
    
    private class ConditionalHandler : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            try {
                val query = exchange.requestURI.query
                val params = CustomHttpServer.parseQuery(query)
                
                val conditionalExecutor = ConditionalExecutor.getInstance()
                val actionService = ActionExecutorService.getInstance()
                
                // Check for OR chains (pipe-separated)
                val actions = params["actions"]
                if (actions != null && actions.contains("|")) {
                    val condition = conditionalExecutor.parseOrChains(actions)
                    val forceMode = params["force"]?.toBoolean() ?: false
                    val result = conditionalExecutor.executeConditional(condition, forceMode)
                    
                    val executedJson = result.executedActions.joinToString(",") { "\"$it\"" }
                    val response = """{
                        "success":${result.success},
                        "executedActions":[$executedJson],
                        "message":${result.message?.let { "\"$it\"" } ?: "null"},
                        "error":${result.error?.let { "\"$it\"" } ?: "null"}
                    }"""
                    CustomHttpServer.sendResponse(exchange, 200, response)
                    return
                }
                
                // Check for if-then-else
                val ifCheck = params["if"]
                val thenActions = params["then"]
                val elseActions = params["else"]
                
                if (ifCheck != null && thenActions != null) {
                    val condition = conditionalExecutor.parseConditionalFromArgs(
                        check = ifCheck,
                        thenActions = thenActions,
                        elseActions = elseActions
                    )
                    
                    if (condition != null) {
                        val forceMode = params["force"]?.toBoolean() ?: false
                        val result = conditionalExecutor.executeConditional(condition, forceMode)
                        
                        val executedJson = result.executedActions.joinToString(",") { "\"$it\"" }
                        val response = """{
                            "success":${result.success},
                            "executedActions":[$executedJson],
                            "message":${result.message?.let { "\"$it\"" } ?: "null"},
                            "error":${result.error?.let { "\"$it\"" } ?: "null"},
                            "conditionMet":${result.conditionMet}
                        }"""
                        CustomHttpServer.sendResponse(exchange, 200, response)
                        return
                    }
                }
                
                // Fallback to regular execution
                if (actions != null) {
                    val actionList = actions.split(",")
                    val forceMode = params["force"]?.toBoolean() ?: false
                    
                    // Execute with appropriate mode
                    val results = if (forceMode) {
                        // In force mode, continue even on failures
                        actionList.map { actionService.executeAction(it) }
                    } else {
                        // Normal mode, stop on first failure
                        val results = mutableListOf<ActionExecutorService.ExecutionResult>()
                        for (action in actionList) {
                            val result = actionService.executeAction(action)
                            results.add(result)
                            if (!result.success) break
                        }
                        results
                    }
                    
                    val success = forceMode || results.all { it.success }
                    val resultsJson = results.map { CustomHttpServer.toJson(it) }.joinToString(",")
                    val response = """{
                        "success":$success,
                        "results":[$resultsJson]
                    }"""
                    CustomHttpServer.sendResponse(exchange, 200, response)
                } else {
                    val error = """{"success":false,"error":"No valid conditional or actions provided"}"""
                    CustomHttpServer.sendResponse(exchange, 400, error)
                }
            } catch (e: Exception) {
                val error = """{"success":false,"error":"${e.message}"}"""
                CustomHttpServer.sendResponse(exchange, 500, error)
            }
        }
    }
    
    companion object {
        private val LOG = Logger.getInstance(CustomHttpServer::class.java)
        
        private fun parseQuery(query: String?): Map<String, String> {
            if (query == null) return emptyMap()
            
            return query.split("&")
                .mapNotNull { 
                    val parts = it.split("=", limit = 2)
                    if (parts.size == 2) {
                        URLDecoder.decode(parts[0], "UTF-8") to URLDecoder.decode(parts[1], "UTF-8")
                    } else null
                }
                .toMap()
        }
        
        private fun sendResponse(exchange: HttpExchange, statusCode: Int, response: String) {
            val bytes = response.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.responseHeaders.add("Access-Control-Allow-Origin", "*")
            exchange.sendResponseHeaders(statusCode, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        
        private fun toJson(result: ActionExecutorService.ExecutionResult): String {
            val error = if (result.error != null) "\"${result.error}\"" else "null"
            val message = if (result.message != null) "\"${result.message}\"" else "null"
            val errorType = if (result.errorType != null) "\"${result.errorType}\"" else "null"
            val suggestion = if (result.suggestion != null) "\"${result.suggestion}\"" else "null"
            val requiredContext = if (result.requiredContext != null) {
                "[${result.requiredContext.joinToString(",") { "\"$it\"" }}]"
            } else "null"
            return """{"success":${result.success},"action":"${result.actionId}","message":$message,"error":$error,"errorType":$errorType,"suggestion":$suggestion,"requiredContext":$requiredContext}"""
        }
        
        private fun toJsonArray(results: List<ActionExecutorService.ExecutionResult>): String {
            val actions = results.map { "\"${it.actionId}\"" }.joinToString(",")
            val resultsJson = results.map { toJson(it) }.joinToString(",")
            val success = results.all { it.success }
            return """{"success":$success,"actions":[$actions],"results":[$resultsJson]}"""
        }
    }
}