package com.githeatmap.model

data class CommitEvent(
    val hash: String,
    val timestamp: Long,
    val author: String,
    val message: String,
    val files: List<FileChange>,
    val repoId: String = "",
    val repoName: String = ""
)
