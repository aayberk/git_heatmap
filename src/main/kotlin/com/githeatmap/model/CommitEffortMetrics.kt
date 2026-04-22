package com.githeatmap.model

data class CommitEffortMetrics(
    val commitHash: String,
    val author: String,
    val timestamp: Long,
    val effort: EstimatedEffort,
    val repoId: String = "",
    val repoName: String = ""
)
