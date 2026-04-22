package com.githeatmap.engine

import com.githeatmap.model.FileMetrics
import kotlin.math.ln

class HeatScoreCalculator(
    private val commitWeight: Double = 0.3,
    private val changeWeight: Double = 0.25,
    private val recencyWeight: Double = 0.2,
    private val authorWeight: Double = 0.15,
    private val churnWeight: Double = 0.1
) {
    fun calculate(metrics: List<FileMetrics>, now: Long = System.currentTimeMillis() / 1000): List<ScoredFile> {
        if (metrics.isEmpty()) return emptyList()

        val maxCommits = metrics.maxOf { it.commitCount.toDouble() }
        val maxChange = metrics.maxOf { (it.totalAdded + it.totalDeleted).toDouble() }
        val maxAuthors = metrics.maxOf { it.authors.size.toDouble() }
        val maxChurn = metrics.maxOf { it.churnScore }.takeIf { it > 0 } ?: 1.0

        return metrics.map { metric ->
            val commitScore = metric.commitCount / maxCommits
            val changeScore = (metric.totalAdded + metric.totalDeleted) / maxChange
            val recencyScore = calculateRecencyScore(metric.lastTouched, now)
            val authorScore = metric.authors.size / maxAuthors
            val churnScore = metric.churnScore / maxChurn

            val heat = commitWeight * commitScore +
                       changeWeight * changeScore +
                       recencyWeight * recencyScore +
                       authorWeight * authorScore +
                       churnWeight * churnScore

            ScoredFile(metric, heat)
        }.sortedByDescending { it.heatScore }
    }

    private fun calculateRecencyScore(timestamp: Long, now: Long): Double {
        val daysSinceLastTouch = (now - timestamp) / 86400.0
        return 1.0 / (1.0 + ln(1.0 + daysSinceLastTouch))
    }
}

data class ScoredFile(
    val metrics: FileMetrics,
    val heatScore: Double
)
