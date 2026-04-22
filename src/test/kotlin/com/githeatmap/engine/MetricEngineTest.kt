package com.githeatmap.engine

import com.githeatmap.model.CommitEvent
import com.githeatmap.model.FileChange
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals

class MetricEngineTest {

    @Test
    fun `aggregate should group commits by file`() {
        val commits = listOf(
            CommitEvent("hash1", 1000L, "author1", "msg1", listOf(
                FileChange("file1.kt", 10, 5),
                FileChange("file2.kt", 5, 2)
            )),
            CommitEvent("hash2", 2000L, "author2", "msg2", listOf(
                FileChange("file1.kt", 3, 1)
            ))
        )

        val result = MetricEngine().aggregate(commits)

        assertEquals(2, result.size)
        val file1 = result.find { it.path == "file1.kt" }!!
        assertEquals(2, file1.commitCount)
        assertEquals(13, file1.totalAdded)
        assertEquals(6, file1.totalDeleted)
        assertEquals(setOf("author1", "author2"), file1.authors)
    }

    @Test
    fun `aggregate should handle empty commits`() {
        val result = MetricEngine().aggregate(emptyList())
        assertEquals(0, result.size)
    }

    @Test
    fun `aggregate should calculate churn score`() {
        val commits = listOf(
            CommitEvent("hash1", 1000L, "author1", "msg1", listOf(
                FileChange("file1.kt", 10, 5)
            ))
        )

        val result = MetricEngine().aggregate(commits)

        assertEquals(5.0, result.first().churnScore)
    }

    @Test
    fun `aggregate should preserve results when processing in small batches`() {
        val commits = (1..10).map { index ->
            CommitEvent(
                hash = "hash$index",
                timestamp = 1000L + index,
                author = "author${index % 2}",
                message = "msg$index",
                files = listOf(
                    FileChange("shared.kt", index, index - 1),
                    FileChange("file$index.kt", 1, 0)
                )
            )
        }

        val result = MetricEngine().aggregate(commits, batchSize = 2)
        val shared = result.find { it.path == "shared.kt" }!!

        assertEquals(11, result.size)
        assertEquals(10, shared.commitCount)
        assertEquals((1..10).sum(), shared.totalAdded)
        assertEquals((0..9).sum(), shared.totalDeleted)
    }
}
