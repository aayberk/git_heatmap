package com.githeatmap.ui

import com.githeatmap.engine.ScoredFile
import com.githeatmap.model.FileMetrics
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HeatmapPanelTest {

    @Test
    fun `getFileAt should return file for populated cell`() {
        val panel = HeatmapPanel().apply {
            setSize(200, 200)
            files = listOf(
                scoredFile("src/App.kt", 0.9),
                scoredFile("src/Util.kt", 0.5)
            )
        }

        val file = panel.getFileAt(12, 12)

        assertEquals("src/App.kt", file?.metrics?.path)
    }

    @Test
    fun `getFileAt should return null outside cells`() {
        val panel = HeatmapPanel().apply {
            setSize(200, 200)
            files = listOf(scoredFile("src/App.kt", 0.9))
        }

        assertNull(panel.getFileAt(500, 500))
    }

    @Test
    fun `preferred size should grow with file count`() {
        val panel = HeatmapPanel().apply {
            setSize(120, 120)
            files = (1..200).map { scoredFile("src/File$it.kt", 0.4) }
        }

        assertTrue(panel.preferredSize.height > 200)
    }

    private fun scoredFile(path: String, heat: Double): ScoredFile {
        return ScoredFile(
            metrics = FileMetrics(
                path = path,
                commitCount = 1,
                totalAdded = 10,
                totalDeleted = 2,
                lastTouched = 1000L,
                authors = setOf("alice"),
                churnScore = 8.0
            ),
            heatScore = heat
        )
    }
}
