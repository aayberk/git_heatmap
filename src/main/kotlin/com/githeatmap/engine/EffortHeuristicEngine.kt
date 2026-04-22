package com.githeatmap.engine

import com.githeatmap.model.CommitEvent
import com.githeatmap.model.CommitEffortMetrics
import com.githeatmap.model.EstimatedEffort
import com.githeatmap.model.FileChange
import kotlin.math.sqrt

class EffortHeuristicEngine {

    fun estimateCommit(commit: CommitEvent): CommitEffortMetrics {
        return CommitEffortMetrics(
            commitHash = commit.hash,
            author = commit.author,
            timestamp = commit.timestamp,
            effort = estimate(
                changes = commit.files,
                commitMessage = commit.message
            ),
            repoId = commit.repoId,
            repoName = commit.repoName,
        )
    }

    fun estimateDiff(changes: List<FileChange>): EstimatedEffort {
        return estimate(changes, commitMessage = null)
    }

    private fun estimate(changes: List<FileChange>, commitMessage: String?): EstimatedEffort {
        if (changes.isEmpty()) return EstimatedEffort.ZERO

        val fileCount = changes.size
        val totalAdded = changes.sumOf { it.addedLines }
        val totalDeleted = changes.sumOf { it.deletedLines }
        val churn = totalAdded + totalDeleted
        val directorySpread = changes.map { topLevelDirectory(it.path) }.distinct().size
        val testFileCount = changes.count { isTestPath(it.path) }
        val riskyFileCount = changes.count { isRiskyPath(it.path) }
        val configFileCount = changes.count { isConfigPath(it.path) }
        val docOnly = changes.all { isDocumentationPath(it.path) }
        val codeFileCount = changes.count { isCodePath(it.path) }

        val fileCountScore = fileCount * 2.1
        val churnScore = sqrt(churn.toDouble()) / 2.6
        val spreadScore = directorySpread * 1.5
        val testScore = testFileCount * 0.9
        val riskScore = riskyFileCount * 2.4
        val configScore = configFileCount * 1.2
        val codeScore = codeFileCount * 0.7
        val trivialityDiscount = trivialityDiscount(changes, commitMessage)

        val score = (fileCountScore + churnScore + spreadScore + testScore + riskScore + configScore + codeScore - trivialityDiscount)
            .coerceAtLeast(0.0)

        val (minMinutes, maxMinutes) = scoreToMinutes(score)
        val confidence = confidenceFor(
            changes = changes,
            testFileCount = testFileCount,
            riskyFileCount = riskyFileCount,
            docOnly = docOnly
        )
        val reasons = buildReasons(
            fileCount = fileCount,
            churn = churn,
            directorySpread = directorySpread,
            testFileCount = testFileCount,
            riskyFileCount = riskyFileCount,
            configFileCount = configFileCount,
            docOnly = docOnly
        )

        return EstimatedEffort(
            minMinutes = minMinutes,
            maxMinutes = maxMinutes,
            score = score,
            confidence = confidence,
            reasons = reasons
        )
    }

    private fun confidenceFor(
        changes: List<FileChange>,
        testFileCount: Int,
        riskyFileCount: Int,
        docOnly: Boolean
    ): Double {
        var confidence = 0.56
        if (docOnly) confidence += 0.2
        if (testFileCount > 0) confidence += 0.12
        if (riskyFileCount > 0) confidence -= 0.08
        if (changes.size > 12) confidence -= 0.05
        return confidence.coerceIn(0.2, 0.92)
    }

    private fun buildReasons(
        fileCount: Int,
        churn: Int,
        directorySpread: Int,
        testFileCount: Int,
        riskyFileCount: Int,
        configFileCount: Int,
        docOnly: Boolean
    ): List<String> {
        val reasons = mutableListOf<String>()
        if (docOnly) reasons += "documentation-only change"
        if (fileCount > 0) reasons += "$fileCount files changed"
        if (churn > 0) reasons += "$churn churn lines"
        if (directorySpread > 1) reasons += "$directorySpread top-level areas touched"
        if (testFileCount > 0) reasons += "$testFileCount test files"
        if (riskyFileCount > 0) reasons += "higher-risk paths affected"
        if (configFileCount > 0) reasons += "config/build changes present"
        return reasons.take(4)
    }

    private fun scoreToMinutes(score: Double): Pair<Int, Int> {
        return when {
            score <= 8.0 -> 15 to 30
            score <= 18.0 -> 30 to 90
            score <= 32.0 -> 90 to 240
            score <= 52.0 -> 240 to 480
            score <= 80.0 -> 480 to 960
            else -> 960 to 1440
        }
    }

    private fun trivialityDiscount(changes: List<FileChange>, commitMessage: String?): Double {
        val message = commitMessage.orEmpty().lowercase()
        val docOnly = changes.all { isDocumentationPath(it.path) }
        val tinyChange = changes.sumOf { it.addedLines + it.deletedLines } <= 12
        return when {
            docOnly -> 4.5
            tinyChange && (message.contains("typo") || message.contains("docs") || message.contains("readme")) -> 3.0
            changes.all { isConfigPath(it.path) } && tinyChange -> 2.0
            else -> 0.0
        }
    }

    private fun topLevelDirectory(path: String): String {
        return path.substringBefore('/').ifBlank { path }
    }

    private fun isTestPath(path: String): Boolean {
        val lowerPath = path.lowercase()
        return "/test/" in lowerPath ||
            lowerPath.contains("test") ||
            lowerPath.endsWith("test.kt") ||
            lowerPath.endsWith("spec.ts") ||
            lowerPath.endsWith("spec.tsx")
    }

    private fun isRiskyPath(path: String): Boolean {
        val lowerPath = path.lowercase()
        return lowerPath.endsWith(".sql") ||
            lowerPath.contains("migration") ||
            lowerPath.contains("auth") ||
            lowerPath.contains("security") ||
            lowerPath.contains("payment") ||
            lowerPath.contains("infra")
    }

    private fun isConfigPath(path: String): Boolean {
        val lowerPath = path.lowercase()
        return lowerPath.endsWith(".yml") ||
            lowerPath.endsWith(".yaml") ||
            lowerPath.endsWith(".json") ||
            lowerPath.endsWith(".xml") ||
            lowerPath.endsWith(".gradle") ||
            lowerPath.endsWith(".gradle.kts") ||
            lowerPath.endsWith(".properties")
    }

    private fun isDocumentationPath(path: String): Boolean {
        val lowerPath = path.lowercase()
        return lowerPath.endsWith(".md") || lowerPath.endsWith(".txt") || lowerPath.endsWith(".adoc")
    }

    private fun isCodePath(path: String): Boolean {
        val lowerPath = path.lowercase()
        return lowerPath.endsWith(".kt") ||
            lowerPath.endsWith(".java") ||
            lowerPath.endsWith(".ts") ||
            lowerPath.endsWith(".tsx") ||
            lowerPath.endsWith(".js") ||
            lowerPath.endsWith(".jsx") ||
            lowerPath.endsWith(".py") ||
            lowerPath.endsWith(".go") ||
            lowerPath.endsWith(".rb")
    }
}
