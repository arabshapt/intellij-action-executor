package com.leaderkey.intellij

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/**
 * Manages the lifecycle of the custom HTTP server and Unix socket server.
 * Starts both servers when the plugin loads and stops them when unloading.
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

                val socketService = ApplicationManager.getApplication().getService(UnixSocketServerService::class.java)
                socketService.startServer()

                serverStarted = true
                LOG.info("HTTP and Unix socket servers initialized from project startup")
            } catch (e: Exception) {
                LOG.error("Failed to start servers", e)
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

/**
 * Application-level service to manage the Unix domain socket server
 */
@Service(Service.Level.APP)
class UnixSocketServerService {
    companion object {
        private val LOG = Logger.getInstance(UnixSocketServerService::class.java)
    }

    private var server: UnixSocketServer? = null

    fun startServer() {
        if (server == null) {
            server = UnixSocketServer()
            server?.start()
            LOG.info("Unix socket server service started")
        }
    }

    fun stopServer() {
        server?.stop()
        server = null
        LOG.info("Unix socket server service stopped")
    }

    fun dispose() {
        stopServer()
    }
}
