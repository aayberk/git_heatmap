package com.githeatmap.cache

import com.githeatmap.model.CommitEvent
import com.githeatmap.model.FileChange
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class CommitCacheTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `save and load should preserve commits`() {
        val cache = CommitCache("/repo/path", tempDir)
        val commits = listOf(
            CommitEvent(
                hash = "abc",
                timestamp = 100L,
                author = "alice",
                message = "first",
                files = listOf(FileChange("src/App.kt", 10, 2))
            )
        )

        cache.save(commits)

        assertEquals(commits, cache.load())
    }

    @Test
    fun `getLastCommitHash should return first commit hash`() {
        val cache = CommitCache("/repo/path", tempDir)
        cache.save(
            listOf(
                CommitEvent("head", 200L, "alice", "latest", emptyList()),
                CommitEvent("older", 100L, "bob", "older", emptyList())
            )
        )

        assertEquals("head", cache.getLastCommitHash())
    }

    @Test
    fun `clear should remove persisted cache`() {
        val cache = CommitCache("/repo/path", tempDir)
        cache.save(listOf(CommitEvent("head", 200L, "alice", "latest", emptyList())))

        cache.clear()

        assertEquals(emptyList<CommitEvent>(), cache.load())
        assertNull(cache.getLastCommitHash())
    }
}
