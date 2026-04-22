package com.githeatmap.engine

import com.githeatmap.model.CommitEffortMetrics
import com.githeatmap.model.EstimatedEffort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EffortAggregatorTest {

    private val aggregator = EffortAggregator()

    @Test
    fun `summarizeAuthorEffort should accumulate effort ranges and buckets`() {
        val commitEfforts = listOf(
            CommitEffortMetrics("c1", "alice", 1L, EstimatedEffort(15, 30, 2.0, 0.8, emptyList())),
            CommitEffortMetrics("c2", "alice", 2L, EstimatedEffort(90, 240, 20.0, 0.7, emptyList())),
            CommitEffortMetrics("c3", "bob", 3L, EstimatedEffort(30, 90, 10.0, 0.6, emptyList()))
        )

        val result = aggregator.summarizeAuthorEffort(commitEfforts)

        assertEquals(105, result["alice"]?.totalMinMinutes)
        assertEquals(270, result["alice"]?.totalMaxMinutes)
        assertEquals(1, result["alice"]?.buckets?.get("0-30 dk"))
        assertEquals(1, result["alice"]?.buckets?.get("2 saat-4 saat"))
        assertEquals(30, result["bob"]?.totalMinMinutes)
    }

    @Test
    fun `summarizeTotalEffort should sum ranges`() {
        val total = aggregator.summarizeTotalEffort(
            listOf(
                CommitEffortMetrics("c1", "alice", 1L, EstimatedEffort(15, 30, 2.0, 0.8, emptyList())),
                CommitEffortMetrics("c2", "alice", 2L, EstimatedEffort(30, 90, 10.0, 0.6, emptyList()))
            )
        )

        assertEquals(45, total.minMinutes)
        assertEquals(120, total.maxMinutes)
    }

    @Test
    fun `effortBucketLabel should distinguish large ranges better`() {
        val mediumLarge = aggregator.effortBucketLabel(
            EstimatedEffort(1500, 2000, 90.0, 0.5, emptyList())
        )
        val veryLarge = aggregator.effortBucketLabel(
            EstimatedEffort(4000, 5000, 120.0, 0.4, emptyList())
        )

        assertEquals("2 gün-4 gün", mediumLarge)
        assertEquals("8 gün+", veryLarge)
    }
}
