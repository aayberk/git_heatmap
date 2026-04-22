package com.githeatmap.engine

import com.githeatmap.model.CommitEvent
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class DateRangeFilter(
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null
) {
    val isAllTime: Boolean
        get() = startDate == null && endDate == null
}

object TimeFilter {
    fun filter(
        commits: List<CommitEvent>,
        range: DateRangeFilter,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): List<CommitEvent> {
        if (range.isAllTime) return commits

        val startTimestamp = range.startDate
            ?.atStartOfDay(zoneId)
            ?.toEpochSecond()

        val endExclusiveTimestamp = range.endDate
            ?.plusDays(1)
            ?.atStartOfDay(zoneId)
            ?.toEpochSecond()

        return commits.filter { commit ->
            (startTimestamp == null || commit.timestamp >= startTimestamp) &&
                (endExclusiveTimestamp == null || commit.timestamp < endExclusiveTimestamp)
        }
    }

    fun toLocalDate(timestamp: Long, zoneId: ZoneId = ZoneId.systemDefault()): LocalDate {
        return Instant.ofEpochSecond(timestamp).atZone(zoneId).toLocalDate()
    }
}
