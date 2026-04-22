package com.githeatmap.model

data class FileMetrics(
    val path: String,
    val commitCount: Int,
    val totalAdded: Int,
    val totalDeleted: Int,
    val lastTouched: Long,
    val authors: Set<String>,
    val churnScore: Double
)
