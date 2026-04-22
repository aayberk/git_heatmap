package com.githeatmap.engine

import com.githeatmap.model.CommitEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneOffset

class TimeFilterTest {

    @Test
    fun `filter should return all commits when no dates are selected`() {
        val commits = listOf(
            CommitEvent("hash1", 1000L, "author", "msg", emptyList()),
            CommitEvent("hash2", 2000L, "author", "msg", emptyList())
        )

        val result = TimeFilter.filter(commits, DateRangeFilter(), ZoneOffset.UTC)

        assertEquals(2, result.size)
    }

    @Test
    fun `filter should apply start and end dates inclusively`() {
        val commits = listOf(
            commitOn("hash1", LocalDate.of(2026, 4, 1)),
            commitOn("hash2", LocalDate.of(2026, 4, 15)),
            commitOn("hash3", LocalDate.of(2026, 4, 30))
        )

        val result = TimeFilter.filter(
            commits,
            DateRangeFilter(
                startDate = LocalDate.of(2026, 4, 10),
                endDate = LocalDate.of(2026, 4, 30)
            ),
            ZoneOffset.UTC
        )

        assertEquals(listOf("hash2", "hash3"), result.map { it.hash })
    }

    @Test
    fun `filter should apply open ended start date`() {
        val commits = listOf(
            commitOn("hash1", LocalDate.of(2026, 4, 1)),
            commitOn("hash2", LocalDate.of(2026, 4, 15))
        )

        val result = TimeFilter.filter(
            commits,
            DateRangeFilter(startDate = LocalDate.of(2026, 4, 10)),
            ZoneOffset.UTC
        )

        assertEquals(listOf("hash2"), result.map { it.hash })
    }

    @Test
    fun `filter should apply open ended end date`() {
        val commits = listOf(
            commitOn("hash1", LocalDate.of(2026, 4, 1)),
            commitOn("hash2", LocalDate.of(2026, 4, 15))
        )

        val result = TimeFilter.filter(
            commits,
            DateRangeFilter(endDate = LocalDate.of(2026, 4, 10)),
            ZoneOffset.UTC
        )

        assertEquals(listOf("hash1"), result.map { it.hash })
    }

    @Test
    fun `filter should handle empty commits`() {
        val result = TimeFilter.filter(emptyList(), DateRangeFilter(), ZoneOffset.UTC)
        assertEquals(0, result.size)
    }

    private fun commitOn(hash: String, date: LocalDate): CommitEvent {
        return CommitEvent(
            hash = hash,
            timestamp = date.atStartOfDay().toEpochSecond(ZoneOffset.UTC),
            author = "author",
            message = "msg",
            files = emptyList()
        )
    }
}
