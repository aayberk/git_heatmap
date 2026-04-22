package com.githeatmap.engine

import com.githeatmap.model.AuthorEffortSummary
import com.githeatmap.model.CommitEffortMetrics
import com.githeatmap.model.EstimatedEffort

class EffortAggregator {

    fun summarizeAuthorEffort(commitEfforts: List<CommitEffortMetrics>): Map<String, AuthorEffortSummary> {
        val grouped = linkedMapOf<String, MutableAuthorEffort>()

        commitEfforts.forEach { commitEffort ->
            val summary = grouped.getOrPut(commitEffort.author) { MutableAuthorEffort(commitEffort.author) }
            summary.totalMinMinutes += commitEffort.effort.minMinutes
            summary.totalMaxMinutes += commitEffort.effort.maxMinutes
            val bucket = effortBucketLabel(commitEffort.effort)
            summary.buckets[bucket] = (summary.buckets[bucket] ?: 0) + 1
        }

        return grouped.mapValues { (_, value) ->
            AuthorEffortSummary(
                author = value.author,
                totalMinMinutes = value.totalMinMinutes,
                totalMaxMinutes = value.totalMaxMinutes,
                buckets = value.buckets.toMap()
            )
        }
    }

    fun summarizeTotalEffort(commitEfforts: List<CommitEffortMetrics>): EstimatedEffort {
        if (commitEfforts.isEmpty()) return EstimatedEffort.ZERO

        val minMinutes = commitEfforts.sumOf { it.effort.minMinutes }
        val maxMinutes = commitEfforts.sumOf { it.effort.maxMinutes }
        val avgScore = commitEfforts.map { it.effort.score }.average()
        val avgConfidence = commitEfforts.map { it.effort.confidence }.average()
        return EstimatedEffort(
            minMinutes = minMinutes,
            maxMinutes = maxMinutes,
            score = avgScore,
            confidence = avgConfidence,
            reasons = emptyList()
        )
    }

    fun effortBucketLabel(effort: EstimatedEffort): String {
        val representativeMinutes = (effort.minMinutes + effort.maxMinutes) / 2
        return bucketLabelForMinutes(representativeMinutes)
    }

    private fun bucketLabelForMinutes(minutes: Int): String {
        if (minutes <= BASE_BUCKET_MINUTES) {
            return "0-${formatBucketBound(BASE_BUCKET_MINUTES)}"
        }

        var lower = BASE_BUCKET_MINUTES
        var upper = BASE_BUCKET_MINUTES * 2
        while (minutes > upper && upper < MAX_CLOSED_BUCKET_MINUTES) {
            lower = upper
            upper *= 2
        }

        return if (minutes > MAX_CLOSED_BUCKET_MINUTES) {
            "${formatBucketBound(MAX_CLOSED_BUCKET_MINUTES)}+"
        } else {
            "${formatBucketBound(lower)}-${formatBucketBound(upper)}"
        }
    }

    private fun formatBucketBound(minutes: Int): String {
        return when {
            minutes < 60 -> "${minutes} dk"
            minutes < WORK_DAY_MINUTES -> "${minutes / 60} saat"
            else -> "${minutes / WORK_DAY_MINUTES} gün"
        }
    }

    private class MutableAuthorEffort(val author: String) {
        var totalMinMinutes: Int = 0
        var totalMaxMinutes: Int = 0
        val buckets: LinkedHashMap<String, Int> = linkedMapOf()
    }

    companion object {
        private const val BASE_BUCKET_MINUTES = 30
        private const val WORK_DAY_MINUTES = 8 * 60
        private const val MAX_CLOSED_BUCKET_MINUTES = 8 * WORK_DAY_MINUTES
    }
}
