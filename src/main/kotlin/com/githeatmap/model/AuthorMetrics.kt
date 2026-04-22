package com.githeatmap.model

data class AuthorMetrics(
    val author: String,
    val commitCount: Int,
    val totalAdded: Int,
    val totalDeleted: Int,
    val avgChangesPerCommit: Double,
    val effortMinMinutes: Int = 0,
    val effortMaxMinutes: Int = 0,
    val effortBuckets: Map<String, Int> = emptyMap()
) {
    val netLines: Int
        get() = totalAdded - totalDeleted
}
