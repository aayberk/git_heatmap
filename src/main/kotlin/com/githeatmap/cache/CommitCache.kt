package com.githeatmap.cache

import com.githeatmap.model.CommitEvent
import com.githeatmap.model.FileChange
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.intellij.openapi.application.PathManager
import java.io.File

class CommitCache(
    private val projectPath: String,
    private val cacheRoot: File = defaultCacheRoot()
) {

    private val gson = Gson()
    private val cacheFile: File by lazy {
        cacheRoot.mkdirs()
        File(cacheRoot, "${projectPath.hashCode()}-commits.json")
    }

    fun save(commits: List<CommitEvent>) {
        val data = commits.map { it.toSerializable() }
        cacheFile.writeText(gson.toJson(data))
    }

    fun load(): List<CommitEvent> {
        if (!cacheFile.exists()) return emptyList()
        return try {
            val json = cacheFile.readText()
            val data = gson.fromJson<List<SerializableCommit>>(json, object : TypeToken<List<SerializableCommit>>() {}.type)
            data.map { it.toCommitEvent() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getLastCommitHash(): String? = load().firstOrNull()?.hash

    fun clear() {
        if (cacheFile.exists()) {
            cacheFile.delete()
        }
    }

    private data class SerializableCommit(
        val hash: String,
        val timestamp: Long,
        val author: String,
        val message: String,
        val files: List<SerializableFile>,
        val repoId: String,
        val repoName: String
    )

    private data class SerializableFile(
        val path: String,
        val addedLines: Int,
        val deletedLines: Int
    )

    private fun CommitEvent.toSerializable() = SerializableCommit(
        hash = hash,
        timestamp = timestamp,
        author = author,
        message = message,
        files = files.map { SerializableFile(it.path, it.addedLines, it.deletedLines) },
        repoId = repoId,
        repoName = repoName
    )

    private fun SerializableCommit.toCommitEvent() = CommitEvent(
        hash = hash,
        timestamp = timestamp,
        author = author,
        message = message,
        files = files.map { FileChange(it.path, it.addedLines, it.deletedLines) },
        repoId = repoId,
        repoName = repoName
    )

    companion object {
        private fun defaultCacheRoot(): File = File(PathManager.getSystemPath(), "git-heatmap")
    }
}
