package com.githeatmap.engine

import com.githeatmap.model.AuthorFileMetrics
import com.githeatmap.model.AuthorEffortSummary
import com.githeatmap.model.AuthorMetrics
import com.githeatmap.model.CommitEvent

class AuthorStatsCalculator {

    fun calculate(
        commits: List<CommitEvent>,
        effortByAuthor: Map<String, AuthorEffortSummary> = emptyMap()
    ): List<AuthorMetrics> {
        val authorData = mutableMapOf<String, AuthorData>()

        for (commit in commits) {
            val data = authorData.getOrPut(commit.author) { AuthorData(commit.author) }
            data.commitCount++
            data.totalAdded += commit.files.sumOf { it.addedLines }
            data.totalDeleted += commit.files.sumOf { it.deletedLines }
        }

        return authorData.values.map {
            val effort = effortByAuthor[it.author]
            AuthorMetrics(
                author = it.author,
                commitCount = it.commitCount,
                totalAdded = it.totalAdded,
                totalDeleted = it.totalDeleted,
                avgChangesPerCommit = (it.totalAdded + it.totalDeleted).toDouble() / it.commitCount.coerceAtLeast(1),
                effortMinMinutes = effort?.totalMinMinutes ?: 0,
                effortMaxMinutes = effort?.totalMaxMinutes ?: 0,
                effortBuckets = effort?.buckets.orEmpty()
            )
        }.sortedByDescending { it.commitCount }
    }

    fun calculateFileBreakdown(commits: List<CommitEvent>): Map<String, List<AuthorFileMetrics>> {
        val authorFileData = mutableMapOf<String, MutableMap<String, FileData>>()

        for (commit in commits) {
            val filesByAuthor = authorFileData.getOrPut(commit.author) { linkedMapOf() }
            for (file in commit.files) {
                val data = filesByAuthor.getOrPut(file.path) { FileData(file.path) }
                data.commitCount++
                data.totalAdded += file.addedLines
                data.totalDeleted += file.deletedLines
            }
        }

        return authorFileData.mapValues { (_, files) ->
            files.values.map {
                AuthorFileMetrics(
                    path = it.path,
                    commitCount = it.commitCount,
                    totalAdded = it.totalAdded,
                    totalDeleted = it.totalDeleted
                )
            }.sortedWith(
                compareByDescending<AuthorFileMetrics> { kotlin.math.abs(it.netLines) }
                    .thenByDescending { it.commitCount }
                    .thenBy { it.path.lowercase() }
            )
        }
    }

    private class AuthorData(val author: String) {
        var commitCount = 0
        var totalAdded = 0
        var totalDeleted = 0
    }

    private class FileData(val path: String) {
        var commitCount = 0
        var totalAdded = 0
        var totalDeleted = 0
    }
}
