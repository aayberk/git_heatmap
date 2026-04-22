package com.githeatmap.model

data class AuthorEffortSummary(
    val author: String,
    val totalMinMinutes: Int,
    val totalMaxMinutes: Int,
    val buckets: Map<String, Int>
)
