package com.leaderkey.intellij

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/**
 * Manages the lifecycle of the custom HTTP server.
 * Starts the server when the plugin loads and stops it when unloading.
 */
class PluginStartup : ProjectActivity {
    companion object {
        private val LOG = Logger.getInstance(PluginStartup::class.java)
        private var serverStarted = false
    }
    
    override suspend fun execute(project: Project) {
        if (!serverStarted) {
            try {
                val serverService = ApplicationManager.getApplication().getService(CustomHttpServerService::class.java)
                serverService.startServer()
                serverStarted = true
                LOG.info("Custom HTTP server initialized from project startup")
            } catch (e: Exception) {
                LOG.error("Failed to start custom HTTP server", e)
            }
        }
    }
}

/**
 * Application-level service to manage the custom HTTP server
 */
@Service(Service.Level.APP)
class CustomHttpServerService {
    companion object {
        private val LOG = Logger.getInstance(CustomHttpServerService::class.java)
    }
    
    private var server: CustomHttpServer? = null
    
    fun startServer() {
        if (server == null) {
            server = CustomHttpServer(63343)
            server?.start()
            LOG.info("Custom HTTP server service started")
        }
    }
    
    fun stopServer() {
        server?.stop()
        server = null
        LOG.info("Custom HTTP server service stopped")
    }
    
    fun dispose() {
        stopServer()
    }
}