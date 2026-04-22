package com.githeatmap.cache

import com.githeatmap.model.FileMetrics
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.intellij.openapi.application.PathManager
import java.io.File

class MetricsCache(
    private val projectPath: String,
    private val cacheRoot: File = defaultCacheRoot()
) {

    private val gson = Gson()
    private val cacheFile: File by lazy {
        cacheRoot.mkdirs()
        File(cacheRoot, "${projectPath.hashCode()}-metrics.json")
    }

    fun save(lastCommitHash: String, metrics: List<FileMetrics>) {
        val snapshot = SerializableSnapshot(
            lastCommitHash = lastCommitHash,
            metrics = metrics.map { it.toSerializable() }
        )
        cacheFile.writeText(gson.toJson(snapshot))
    }

    fun load(lastCommitHash: String): List<FileMetrics>? {
        if (!cacheFile.exists()) return null
        return try {
            val json = cacheFile.readText()
            val snapshot = gson.fromJson<SerializableSnapshot>(json, object : TypeToken<SerializableSnapshot>() {}.type)
            if (snapshot.lastCommitHash != lastCommitHash) return null
            snapshot.metrics.map { it.toFileMetrics() }
        } catch (_: Exception) {
            null
        }
    }

    fun clear() {
        if (cacheFile.exists()) {
            cacheFile.delete()
        }
    }

    private data class SerializableSnapshot(
        val lastCommitHash: String,
        val metrics: List<SerializableMetric>
    )

    private data class SerializableMetric(
        val path: String,
        val commitCount: Int,
        val totalAdded: Int,
        val totalDeleted: Int,
        val lastTouched: Long,
        val authors: List<String>,
        val churnScore: Double
    )

    private fun FileMetrics.toSerializable() = SerializableMetric(
        path = path,
        commitCount = commitCount,
        totalAdded = totalAdded,
        totalDeleted = totalDeleted,
        lastTouched = lastTouched,
        authors = authors.sorted(),
        churnScore = churnScore
    )

    private fun SerializableMetric.toFileMetrics() = FileMetrics(
        path = path,
        commitCount = commitCount,
        totalAdded = totalAdded,
        totalDeleted = totalDeleted,
        lastTouched = lastTouched,
        authors = authors.toSet(),
        churnScore = churnScore
    )

    companion object {
        private fun defaultCacheRoot(): File = File(PathManager.getSystemPath(), "git-heatmap")
    }
}
