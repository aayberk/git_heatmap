package com.githeatmap.git

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class GitParsingSupportTest {

    @Test
    fun `parseLogOutput should parse commit headers and numstat lines`() {
        val lines = listOf(
            "abc123|1710000000|alice|message with | pipe",
            "10\t3\tsrc/main/App.kt",
            "2\t1\tsrc/{old => new}/Name.kt",
            "",
            "def456|1710000500|bob|second commit",
            "1\t0\tREADME.md"
        )

        val result = GitParsingSupport.parseLogOutput(lines)

        assertEquals(2, result.size)
        assertEquals("abc123", result[0].hash)
        assertEquals(1710000000L, result[0].timestamp)
        assertEquals("alice", result[0].author)
        assertEquals("message with | pipe", result[0].message)
        assertEquals("src/new/Name.kt", result[0].files[1].path)
    }

    @Test
    fun `parseDiffLine should normalize direct rename targets`() {
        val result = GitParsingSupport.parseDiffLine("5\t2\told/path/File.kt => new/path/File.kt")

        assertEquals("new/path/File.kt", result?.path)
        assertEquals(5, result?.addedLines)
        assertEquals(2, result?.deletedLines)
    }

    @Test
    fun `parseDiffLine should normalize brace rename targets`() {
        val result = GitParsingSupport.parseDiffLine("7\t4\tsrc/{legacy => modern}/Feature.kt")

        assertEquals("src/modern/Feature.kt", result?.path)
    }

    @Test
    fun `parseDiffLine should treat binary numstat markers as zero`() {
        val result = GitParsingSupport.parseDiffLine("-\t-\tassets/logo.png")

        assertEquals("assets/logo.png", result?.path)
        assertEquals(0, result?.addedLines)
        assertEquals(0, result?.deletedLines)
    }

    @Test
    fun `parseDiffLine should return null for malformed lines`() {
        assertNull(GitParsingSupport.parseDiffLine("not-a-numstat-line"))
    }
}
