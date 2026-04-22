package com.githeatmap.engine

import com.githeatmap.model.CommitEvent
import com.githeatmap.model.FileMetrics

class MetricEngine {

    fun aggregate(commits: List<CommitEvent>, batchSize: Int = DEFAULT_BATCH_SIZE): List<FileMetrics> {
        val fileData = mutableMapOf<String, MutableFileData>()

        commits.chunked(batchSize.coerceAtLeast(1)).forEach { batch ->
            batch.forEach { commit ->
                commit.files.forEach { file ->
                    val data = fileData.getOrPut(file.path) {
                        MutableFileData(path = file.path)
                    }
                    data.commitCount++
                    data.totalAdded += file.addedLines
                    data.totalDeleted += file.deletedLines
                    data.lastTouched = maxOf(data.lastTouched, commit.timestamp)
                    data.authors.add(commit.author)
                    data.churnScore += kotlin.math.abs(file.addedLines - file.deletedLines)
                }
            }
        }

        return fileData.values.map { it.toFileMetrics() }
    }

    private data class MutableFileData(
        val path: String,
        var commitCount: Int = 0,
        var totalAdded: Int = 0,
        var totalDeleted: Int = 0,
        var lastTouched: Long = 0,
        val authors: MutableSet<String> = mutableSetOf(),
        var churnScore: Double = 0.0
    ) {
        fun toFileMetrics() = FileMetrics(
            path = path,
            commitCount = commitCount,
            totalAdded = totalAdded,
            totalDeleted = totalDeleted,
            lastTouched = lastTouched,
            authors = authors.toSet(),
            churnScore = churnScore
        )
    }

    companion object {
        private const val DEFAULT_BATCH_SIZE = 250
    }
}
