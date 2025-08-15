package com.leaderkey.intellij

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@Service
class ActionHistoryService {
    companion object {
        private val LOG = Logger.getInstance(ActionHistoryService::class.java)
        private val HISTORY_FILE = File(System.getProperty("user.home"), ".intellij-actions/history.json")
        private val STATS_FILE = File(System.getProperty("user.home"), ".intellij-actions/stats.json")
        private const val MAX_HISTORY_ENTRIES = 1000
        private const val PERSIST_INTERVAL_SECONDS = 30L
        
        private val gson: Gson = GsonBuilder()
            .setPrettyPrinting()
            .create()
            
        @JvmStatic
        fun getInstance(): ActionHistoryService = 
            com.intellij.openapi.application.ApplicationManager.getApplication().getService(ActionHistoryService::class.java)
    }
    
    data class HistoryEntry(
        val actionId: String,
        val timestamp: String,
        val success: Boolean,
        val executionTimeMs: Long,
        val errorType: String? = null,
        val chainedWith: List<String>? = null
    )
    
    data class ActionStats(
        val actionId: String,
        val executionCount: Int,
        val successCount: Int,
        val failureCount: Int,
        val averageTimeMs: Long,
        val lastUsed: String,
        val commonlyChainedWith: Map<String, Int> = emptyMap()
    )
    
    data class UsagePattern(
        val sequence: List<String>,
        val frequency: Int,
        val averageTimeMs: Long,
        val suggestion: String? = null
    )
    
    private val history = ConcurrentLinkedQueue<HistoryEntry>()
    private val statsMap = mutableMapOf<String, ActionStats>()
    private val chainPatterns = mutableMapOf<String, AtomicInteger>()
    private val executor = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "ActionHistoryPersister").apply { isDaemon = true }
    }
    
    init {
        loadHistory()
        loadStats()
        
        // Schedule periodic persistence
        executor.scheduleWithFixedDelay(
            { persistData() },
            PERSIST_INTERVAL_SECONDS,
            PERSIST_INTERVAL_SECONDS,
            TimeUnit.SECONDS
        )
        
        // Add shutdown hook to save on exit
        Runtime.getRuntime().addShutdownHook(Thread {
            persistData()
            executor.shutdown()
        })
    }
    
    fun recordExecution(
        actionId: String,
        success: Boolean,
        executionTimeMs: Long,
        errorType: ActionExecutorService.ErrorType? = null,
        chainedWith: List<String>? = null
    ) {
        val entry = HistoryEntry(
            actionId = actionId,
            timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            success = success,
            executionTimeMs = executionTimeMs,
            errorType = errorType?.toString(),
            chainedWith = chainedWith
        )
        
        // Add to history queue
        history.offer(entry)
        
        // Trim history if too large
        while (history.size > MAX_HISTORY_ENTRIES) {
            history.poll()
        }
        
        // Update statistics
        updateStats(entry)
        
        // Track chain patterns
        if (chainedWith != null && chainedWith.isNotEmpty()) {
            val pattern = (listOf(actionId) + chainedWith).joinToString(",")
            chainPatterns.computeIfAbsent(pattern) { AtomicInteger(0) }.incrementAndGet()
        }
    }
    
    private fun updateStats(entry: HistoryEntry) {
        synchronized(statsMap) {
            val current = statsMap[entry.actionId]
            if (current == null) {
                statsMap[entry.actionId] = ActionStats(
                    actionId = entry.actionId,
                    executionCount = 1,
                    successCount = if (entry.success) 1 else 0,
                    failureCount = if (!entry.success) 1 else 0,
                    averageTimeMs = entry.executionTimeMs,
                    lastUsed = entry.timestamp,
                    commonlyChainedWith = entry.chainedWith?.groupingBy { it }?.eachCount() ?: emptyMap()
                )
            } else {
                val newCount = current.executionCount + 1
                val newAvgTime = ((current.averageTimeMs * current.executionCount) + entry.executionTimeMs) / newCount
                
                // Update commonly chained actions
                val updatedChains = current.commonlyChainedWith.toMutableMap()
                entry.chainedWith?.forEach { chainedAction ->
                    updatedChains[chainedAction] = (updatedChains[chainedAction] ?: 0) + 1
                }
                
                statsMap[entry.actionId] = current.copy(
                    executionCount = newCount,
                    successCount = current.successCount + if (entry.success) 1 else 0,
                    failureCount = current.failureCount + if (!entry.success) 1 else 0,
                    averageTimeMs = newAvgTime,
                    lastUsed = entry.timestamp,
                    commonlyChainedWith = updatedChains
                )
            }
        }
    }
    
    fun getRecentHistory(limit: Int = 50): List<HistoryEntry> {
        return history.toList().takeLast(limit).reversed()
    }
    
    fun getTopActions(limit: Int = 20): List<ActionStats> {
        return synchronized(statsMap) {
            statsMap.values
                .sortedByDescending { it.executionCount }
                .take(limit)
        }
    }
    
    fun getActionStats(actionId: String): ActionStats? {
        return synchronized(statsMap) {
            statsMap[actionId]
        }
    }
    
    fun getCommonPatterns(minFrequency: Int = 3): List<UsagePattern> {
        return chainPatterns
            .filter { it.value.get() >= minFrequency }
            .map { (pattern, count) ->
                val actions = pattern.split(",")
                val historyEntries = history.filter { 
                    it.actionId == actions.first() && 
                    it.chainedWith == actions.drop(1)
                }
                val avgTime = if (historyEntries.isNotEmpty()) {
                    historyEntries.map { it.executionTimeMs }.average().toLong()
                } else 0L
                
                UsagePattern(
                    sequence = actions,
                    frequency = count.get(),
                    averageTimeMs = avgTime,
                    suggestion = generateSuggestion(actions, count.get())
                )
            }
            .sortedByDescending { it.frequency }
    }
    
    private fun generateSuggestion(actions: List<String>, frequency: Int): String? {
        return when {
            frequency >= 10 && actions.size >= 3 -> 
                "Consider creating an alias for this frequently used sequence"
            actions.contains("SaveAll") && actions.count { it == "SaveAll" } > 1 ->
                "Duplicate SaveAll detected - consider removing redundant saves"
            actions.all { ActionCategorizer.isInstantAction(it) } ->
                "This sequence executes instantly with smart delays"
            else -> null
        }
    }
    
    fun getSuggestions(): List<String> {
        val suggestions = mutableListOf<String>()
        
        // Suggest based on common patterns
        val patterns = getCommonPatterns()
        patterns.forEach { pattern ->
            pattern.suggestion?.let { suggestions.add(it) }
        }
        
        // Suggest based on failure rates
        synchronized(statsMap) {
            statsMap.values
                .filter { it.failureCount > it.successCount }
                .forEach { stats ->
                    suggestions.add("Action '${stats.actionId}' fails frequently (${stats.failureCount}/${stats.executionCount}). Check requirements with 'ij --explain ${stats.actionId}'")
                }
        }
        
        // Suggest optimizations
        val slowActions = synchronized(statsMap) {
            statsMap.values
                .filter { it.averageTimeMs > 1000 && it.executionCount > 5 }
                .sortedByDescending { it.averageTimeMs }
        }
        
        if (slowActions.isNotEmpty()) {
            suggestions.add("Slow actions detected: ${slowActions.take(3).joinToString { "${it.actionId} (${it.averageTimeMs}ms)" }}")
        }
        
        return suggestions
    }
    
    private fun loadHistory() {
        try {
            if (HISTORY_FILE.exists()) {
                val json = HISTORY_FILE.readText()
                val type = object : TypeToken<List<HistoryEntry>>() {}.type
                val loaded: List<HistoryEntry> = gson.fromJson(json, type)
                history.clear()
                history.addAll(loaded.takeLast(MAX_HISTORY_ENTRIES))
                LOG.info("Loaded ${history.size} history entries")
            }
        } catch (e: Exception) {
            LOG.warn("Failed to load history", e)
        }
    }
    
    private fun loadStats() {
        try {
            if (STATS_FILE.exists()) {
                val json = STATS_FILE.readText()
                val type = object : TypeToken<Map<String, ActionStats>>() {}.type
                val loaded: Map<String, ActionStats> = gson.fromJson(json, type)
                synchronized(statsMap) {
                    statsMap.clear()
                    statsMap.putAll(loaded)
                }
                LOG.info("Loaded statistics for ${statsMap.size} actions")
            }
        } catch (e: Exception) {
            LOG.warn("Failed to load stats", e)
        }
    }
    
    private fun persistData() {
        try {
            // Ensure directory exists
            HISTORY_FILE.parentFile.mkdirs()
            STATS_FILE.parentFile.mkdirs()
            
            // Save history
            val historyJson = gson.toJson(history.toList())
            HISTORY_FILE.writeText(historyJson)
            
            // Save stats
            synchronized(statsMap) {
                val statsJson = gson.toJson(statsMap)
                STATS_FILE.writeText(statsJson)
            }
            
            LOG.debug("Persisted ${history.size} history entries and ${statsMap.size} stats")
        } catch (e: Exception) {
            LOG.error("Failed to persist history data", e)
        }
    }
    
    fun clearHistory() {
        history.clear()
        synchronized(statsMap) {
            statsMap.clear()
        }
        chainPatterns.clear()
        persistData()
        LOG.info("History and statistics cleared")
    }
}