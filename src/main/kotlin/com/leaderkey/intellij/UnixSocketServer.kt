package com.leaderkey.intellij

import com.intellij.openapi.diagnostic.Logger
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Unix Domain Socket server for low-latency IPC with Leader Key.
 * Accepts newline-delimited JSON: {"action":"SaveAll"} or {"actions":"SaveAll,ReformatCode","delay":100}
 * Same payload format as CustomHttpServer's execute endpoint.
 */
class UnixSocketServer(private val socketPath: String = SOCKET_PATH) {

    companion object {
        const val SOCKET_PATH = "/tmp/intellij-leaderkey.sock"
        private val LOG = Logger.getInstance(UnixSocketServer::class.java)
    }

    private var serverChannel: ServerSocketChannel? = null
    private val executor = Executors.newFixedThreadPool(4)
    private val running = AtomicBoolean(false)

    fun start() {
        try {
            // Clean up stale socket file
            val path = Path.of(socketPath)
            Files.deleteIfExists(path)

            val address = UnixDomainSocketAddress.of(path)
            serverChannel = ServerSocketChannel.open(StandardProtocolFamily.UNIX).apply {
                bind(address)
            }
            running.set(true)

            // Accept connections in a background thread
            Thread({
                LOG.info("Unix socket server listening on $socketPath")
                while (running.get()) {
                    try {
                        val client = serverChannel?.accept() ?: break
                        executor.submit { handleClient(client) }
                    } catch (e: Exception) {
                        if (running.get()) {
                            LOG.warn("Error accepting unix socket connection", e)
                        }
                    }
                }
            }, "LeaderKey-UDS-Acceptor").apply {
                isDaemon = true
                start()
            }

            LOG.info("Unix socket server started on $socketPath")
        } catch (e: Exception) {
            LOG.error("Failed to start unix socket server", e)
        }
    }

    fun stop() {
        running.set(false)
        try {
            serverChannel?.close()
            serverChannel = null
            Files.deleteIfExists(Path.of(socketPath))
            executor.shutdown()
            LOG.info("Unix socket server stopped")
        } catch (e: Exception) {
            LOG.warn("Error stopping unix socket server", e)
        }
    }

    private fun handleClient(channel: SocketChannel) {
        try {
            channel.use { ch ->
                val reader = BufferedReader(InputStreamReader(java.nio.channels.Channels.newInputStream(ch)))
                val writer = OutputStreamWriter(java.nio.channels.Channels.newOutputStream(ch))

                val line = reader.readLine() ?: return
                val response = processRequest(line)

                writer.write(response)
                writer.write("\n")
                writer.flush()
            }
        } catch (e: Exception) {
            LOG.warn("Error handling unix socket client", e)
        }
    }

    private fun processRequest(json: String): String {
        try {
            val trimmed = json.trim()
            if (trimmed.isEmpty()) {
                return """{"success":false,"error":"Empty request"}"""
            }

            val actionService = ActionExecutorService.getInstance()

            // Simple JSON parsing — avoid external dependency
            val action = extractJsonString(trimmed, "action")
            val actions = extractJsonString(trimmed, "actions")
            val delay = extractJsonNumber(trimmed, "delay")

            return when {
                action != null -> {
                    val result = actionService.executeAction(action)
                    toJson(result)
                }
                actions != null -> {
                    val actionList = actions.split(",")
                    val results = actionService.executeActions(actionList, delay ?: -1)
                    toJsonArray(results)
                }
                else -> {
                    """{"success":false,"error":"No action or actions field in JSON"}"""
                }
            }
        } catch (e: Exception) {
            LOG.error("Error processing unix socket request: $json", e)
            return """{"success":false,"error":"${e.message?.replace("\"", "\\\"") ?: "Unknown error"}"}"""
        }
    }

    // Minimal JSON field extraction — no external library needed
    private fun extractJsonString(json: String, key: String): String? {
        val pattern = """"$key"\s*:\s*"([^"]*?)"""".toRegex()
        return pattern.find(json)?.groupValues?.get(1)
    }

    private fun extractJsonNumber(json: String, key: String): Long? {
        val pattern = """"$key"\s*:\s*(\d+)""".toRegex()
        return pattern.find(json)?.groupValues?.get(1)?.toLongOrNull()
    }

    private fun toJson(result: ActionExecutorService.ExecutionResult): String {
        val error = if (result.error != null) "\"${result.error}\"" else "null"
        val message = if (result.message != null) "\"${result.message}\"" else "null"
        return """{"success":${result.success},"action":"${result.actionId}","message":$message,"error":$error}"""
    }

    private fun toJsonArray(results: List<ActionExecutorService.ExecutionResult>): String {
        val resultsJson = results.joinToString(",") { toJson(it) }
        val success = results.all { it.success }
        return """{"success":$success,"results":[$resultsJson]}"""
    }
}
