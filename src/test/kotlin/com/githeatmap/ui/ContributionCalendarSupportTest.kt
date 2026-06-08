package com.githeatmap.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneId

class ContributionCalendarSupportTest {

    @Test
    fun `converts git epoch seconds to local date`() {
        val date = ContributionCalendarSupport.epochSecondsToLocalDate(
            timestamp = 1_710_000_000L,
            zoneId = ZoneId.of("UTC")
        )

        assertEquals(LocalDate.of(2024, 3, 9), date)
    }
}
