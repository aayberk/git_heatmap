package com.githeatmap.model

data class FileChange(
    val path: String,
    val addedLines: Int,
    val deletedLines: Int
)
