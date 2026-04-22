package com.githeatmap.engine

import com.githeatmap.model.CommitEvent
import com.githeatmap.model.FileChange
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EffortHeuristicEngineTest {

    private val engine = EffortHeuristicEngine()

    @Test
    fun `estimateCommit should produce documentation discount for tiny docs change`() {
        val commit = CommitEvent(
            hash = "abc",
            timestamp = 1_710_000_000L,
            author = "alice",
            message = "docs: typo fix",
            files = listOf(FileChange("README.md", 2, 1))
        )

        val effort = engine.estimateCommit(commit).effort

        assertEquals(15, effort.minMinutes)
        assertEquals(30, effort.maxMinutes)
        assertTrue(effort.reasons.contains("documentation-only change"))
    }

    @Test
    fun `estimateDiff should produce higher effort for risky multi-file change`() {
        val effort = engine.estimateDiff(
            listOf(
                FileChange("src/main/kotlin/AuthService.kt", 120, 20),
                FileChange("src/main/resources/db/migration/V2__auth.sql", 40, 0),
                FileChange("src/test/kotlin/AuthServiceTest.kt", 80, 5)
            )
        )

        assertTrue(effort.minMinutes >= 90)
        assertTrue(effort.maxMinutes >= effort.minMinutes)
        assertTrue(effort.confidence > 0.0)
        assertTrue(effort.reasons.any { it.contains("test files") || it.contains("higher-risk") })
    }
}
