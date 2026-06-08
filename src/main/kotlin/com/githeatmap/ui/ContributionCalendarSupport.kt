package com.githeatmap.ui

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object ContributionCalendarSupport {
    fun epochSecondsToLocalDate(timestamp: Long, zoneId: ZoneId = ZoneId.systemDefault()): LocalDate {
        return Instant.ofEpochSecond(timestamp).atZone(zoneId).toLocalDate()
    }
}
