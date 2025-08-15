package com.leaderkey.intellij

import com.intellij.openapi.diagnostic.Logger
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.QueryStringDecoder
import org.jetbrains.ide.RestService

class ActionRestService : RestService() {
    companion object {
        private val LOG = Logger.getInstance(ActionRestService::class.java)
    }
    
    override fun getServiceName(): String = "intellij-actions"
    
    override fun isMethodSupported(method: HttpMethod): Boolean {
        return method == HttpMethod.GET || method == HttpMethod.POST
    }
    
    override fun execute(
        urlDecoder: QueryStringDecoder,
        request: FullHttpRequest,
        context: ChannelHandlerContext
    ): String? {
        val path = urlDecoder.path()
        LOG.info("Received request: $path")
        
        return when {
            path.endsWith("/execute") -> handleExecuteAction(urlDecoder)
            path.endsWith("/list") -> handleListActions()
            path.endsWith("/check") -> handleCheckAction(urlDecoder)
            path.endsWith("/health") -> handleHealthCheck()
            else -> createErrorResponse("Unknown endpoint: $path")
        }
    }
    
    private fun handleExecuteAction(urlDecoder: QueryStringDecoder): String {
        return try {
            val parameters = urlDecoder.parameters()
            val actionParam = parameters["action"]?.firstOrNull()
            val actionsParam = parameters["actions"]?.firstOrNull()
            val delayParam = parameters["delay"]?.firstOrNull()?.toLongOrNull() ?: 100L
            
            val service = ActionExecutorService.getInstance()
            
            when {
                // Single action execution
                !actionParam.isNullOrEmpty() -> {
                    LOG.info("Executing single action: $actionParam")
                    val result = service.executeAction(actionParam)
                    toJson(mapOf(
                        "success" to result.success,
                        "action" to result.actionId,
                        "message" to result.message,
                        "error" to result.error
                    ))
                }
                // Multiple actions execution
                !actionsParam.isNullOrEmpty() -> {
                    val actionIds = actionsParam.split(",").map { it.trim() }
                    LOG.info("Executing multiple actions: $actionIds")
                    val results = service.executeActions(actionIds, delayParam)
                    toJson(mapOf(
                        "success" to results.all { it.success },
                        "results" to results.map { result ->
                            mapOf(
                                "action" to result.actionId,
                                "success" to result.success,
                                "message" to result.message,
                                "error" to result.error
                            )
                        }
                    ))
                }
                else -> {
                    createErrorResponse("Missing required parameter: 'action' or 'actions'")
                }
            }
        } catch (e: Exception) {
            LOG.error("Error handling execute action request", e)
            createErrorResponse(e.message ?: "Unknown error")
        }
    }
    
    private fun handleListActions(): String {
        return try {
            val service = ActionExecutorService.getInstance()
            val actionIds = service.getAllActionIds()
            toJson(mapOf(
                "count" to actionIds.size,
                "actions" to actionIds.take(100) // Limit to first 100 for performance
            ))
        } catch (e: Exception) {
            LOG.error("Error listing actions", e)
            createErrorResponse(e.message ?: "Unknown error")
        }
    }
    
    private fun handleCheckAction(urlDecoder: QueryStringDecoder): String {
        return try {
            val parameters = urlDecoder.parameters()
            val actionId = parameters["action"]?.firstOrNull()
            
            if (actionId.isNullOrEmpty()) {
                return createErrorResponse("Missing required parameter: 'action'")
            }
            
            val service = ActionExecutorService.getInstance()
            val isAvailable = service.isActionAvailable(actionId)
            toJson(mapOf(
                "action" to actionId,
                "available" to isAvailable
            ))
        } catch (e: Exception) {
            LOG.error("Error checking action", e)
            createErrorResponse(e.message ?: "Unknown error")
        }
    }
    
    private fun handleHealthCheck(): String {
        val response = mapOf(
            "status" to "healthy",
            "service" to "intellij-action-executor",
            "version" to "1.1.3"
        )
        return toJson(response)
    }
    
    private fun createErrorResponse(message: String): String {
        val errorResponse = mapOf(
            "error" to true,
            "message" to message
        )
        return toJson(errorResponse)
    }
    
    // Simple JSON serialization without external dependencies
    private fun toJson(data: Any?): String {
        return when (data) {
            null -> "null"
            is Boolean -> data.toString()
            is Number -> data.toString()
            is String -> "\"${data.replace("\"", "\\\"")}\""
            is Map<*, *> -> {
                val entries = data.entries.joinToString(",") { (k, v) ->
                    "${toJson(k.toString())}:${toJson(v)}"
                }
                "{$entries}"
            }
            is List<*> -> {
                val items = data.joinToString(",") { toJson(it) }
                "[$items]"
            }
            else -> toJson(data.toString())
        }
    }
    
    override fun isHostTrusted(request: FullHttpRequest, urlDecoder: QueryStringDecoder): Boolean {
        // Only allow localhost connections for security
        val host = request.headers()["Host"] ?: return false
        return host.startsWith("localhost") || host.startsWith("127.0.0.1")
    }
}