package com.githeatmap.cache

import com.githeatmap.model.FileMetrics
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class MetricsCacheTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `save and load should preserve metrics for matching head`() {
        val cache = MetricsCache("/repo/path", tempDir)
        val metrics = listOf(
            FileMetrics(
                path = "src/App.kt",
                commitCount = 3,
                totalAdded = 15,
                totalDeleted = 4,
                lastTouched = 1234L,
                authors = setOf("alice", "bob"),
                churnScore = 8.0
            )
        )

        cache.save("head123", metrics)

        assertEquals(metrics, cache.load("head123"))
    }

    @Test
    fun `load should return null when cached head does not match`() {
        val cache = MetricsCache("/repo/path", tempDir)
        cache.save("head123", emptyList())

        assertNull(cache.load("other-head"))
    }

    @Test
    fun `clear should remove persisted metrics cache`() {
        val cache = MetricsCache("/repo/path", tempDir)
        cache.save("head123", emptyList())

        cache.clear()

        assertNull(cache.load("head123"))
    }
}
