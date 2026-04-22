package com.githeatmap.model

data class AuthorFileMetrics(
    val path: String,
    val commitCount: Int,
    val totalAdded: Int,
    val totalDeleted: Int
) {
    val netLines: Int
        get() = totalAdded - totalDeleted
}
