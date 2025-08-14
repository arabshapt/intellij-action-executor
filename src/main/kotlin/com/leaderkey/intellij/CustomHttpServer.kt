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
                        val delay = params["delay"]?.toLongOrNull() ?: 100
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
            val response = """{"status":"healthy","plugin":"intellij-action-executor","version":"1.0.8","server":"custom","port":63343}"""
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
            return """{"success":${result.success},"action":"${result.actionId}","message":$message,"error":$error}"""
        }
        
        private fun toJsonArray(results: List<ActionExecutorService.ExecutionResult>): String {
            val actions = results.map { "\"${it.actionId}\"" }.joinToString(",")
            val resultsJson = results.map { toJson(it) }.joinToString(",")
            val success = results.all { it.success }
            return """{"success":$success,"actions":[$actions],"results":[$resultsJson]}"""
        }
    }
}