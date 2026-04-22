package com.githeatmap.engine

import com.githeatmap.model.CommitEvent
import com.githeatmap.model.FileChange
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals

class AuthorStatsCalculatorTest {

    @Test
    fun `calculate should group by author`() {
        val commits = listOf(
            CommitEvent("hash1", 1000L, "alice", "msg1", listOf(FileChange("f1.kt", 10, 5))),
            CommitEvent("hash2", 2000L, "bob", "msg2", listOf(FileChange("f2.kt", 5, 2))),
            CommitEvent("hash3", 3000L, "alice", "msg3", listOf(FileChange("f3.kt", 3, 1)))
        )

        val result = AuthorStatsCalculator().calculate(commits)

        assertEquals(2, result.size)
        val alice = result.find { it.author == "alice" }!!
        assertEquals(2, alice.commitCount)
        assertEquals(13, alice.totalAdded)
        assertEquals(6, alice.totalDeleted)
    }

    @Test
    fun `calculate should sort by commit count descending`() {
        val commits = listOf(
            CommitEvent("hash1", 1000L, "alice", "msg1", emptyList()),
            CommitEvent("hash2", 2000L, "bob", "msg2", emptyList()),
            CommitEvent("hash3", 3000L, "bob", "msg3", emptyList())
        )

        val result = AuthorStatsCalculator().calculate(commits)

        assertEquals("bob", result[0].author)
        assertEquals("alice", result[1].author)
    }

    @Test
    fun `calculate should compute avg changes per commit`() {
        val commits = listOf(
            CommitEvent("hash1", 1000L, "alice", "msg1", listOf(FileChange("f1.kt", 10, 5)))
        )

        val result = AuthorStatsCalculator().calculate(commits)

        assertEquals(15.0, result[0].avgChangesPerCommit)
    }

    @Test
    fun `calculateFileBreakdown should aggregate author changes by file`() {
        val commits = listOf(
            CommitEvent("hash1", 1000L, "alice", "msg1", listOf(FileChange("src/A.kt", 10, 2))),
            CommitEvent("hash2", 2000L, "alice", "msg2", listOf(FileChange("src/A.kt", 3, 1), FileChange("src/B.kt", 5, 0))),
            CommitEvent("hash3", 3000L, "bob", "msg3", listOf(FileChange("src/A.kt", 4, 4)))
        )

        val result = AuthorStatsCalculator().calculateFileBreakdown(commits)

        assertEquals(2, result["alice"]?.size)
        val aliceA = result["alice"]!!.first { it.path == "src/A.kt" }
        assertEquals(2, aliceA.commitCount)
        assertEquals(13, aliceA.totalAdded)
        assertEquals(3, aliceA.totalDeleted)
        assertEquals(10, aliceA.netLines)

        val bobA = result["bob"]!!.first { it.path == "src/A.kt" }
        assertEquals(1, bobA.commitCount)
        assertEquals(0, bobA.netLines)
    }
}
