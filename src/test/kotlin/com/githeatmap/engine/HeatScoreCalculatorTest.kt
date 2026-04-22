package com.githeatmap.engine

import com.githeatmap.model.FileMetrics
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

class HeatScoreCalculatorTest {

    @Test
    fun `calculate should return sorted by heat descending`() {
        val metrics = listOf(
            FileMetrics("low.kt", 1, 10, 5, 1000L, setOf("a"), 1.0),
            FileMetrics("high.kt", 100, 1000, 500, System.currentTimeMillis() / 1000, setOf("a", "b", "c"), 100.0)
        )

        val result = HeatScoreCalculator().calculate(metrics)

        assertEquals(2, result.size)
        assertTrue(result[0].heatScore > result[1].heatScore)
        assertEquals("high.kt", result[0].metrics.path)
    }

    @Test
    fun `calculate should handle empty metrics`() {
        val result = HeatScoreCalculator().calculate(emptyList())
        assertEquals(0, result.size)
    }

    @Test
    fun `calculate should normalize scores between 0 and 1`() {
        val metrics = listOf(
            FileMetrics("file.kt", 50, 100, 50, System.currentTimeMillis() / 1000, setOf("a"), 50.0)
        )

        val result = HeatScoreCalculator().calculate(metrics)

        assertTrue(result[0].heatScore in 0.0..1.0)
    }

    @Test
    fun `calculate should give higher score to recent files`() {
        val now = System.currentTimeMillis() / 1000
        val metrics = listOf(
            FileMetrics("old.kt", 10, 100, 50, now - 86400 * 30, setOf("a"), 10.0),
            FileMetrics("new.kt", 10, 100, 50, now, setOf("a"), 10.0)
        )

        val result = HeatScoreCalculator().calculate(metrics, now)

        val newFile = result.find { it.metrics.path == "new.kt" }!!
        val oldFile = result.find { it.metrics.path == "old.kt" }!!
        assertTrue(newFile.heatScore > oldFile.heatScore)
    }
}
