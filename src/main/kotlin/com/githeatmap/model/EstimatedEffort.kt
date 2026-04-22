package com.githeatmap.model

data class EstimatedEffort(
    val minMinutes: Int,
    val maxMinutes: Int,
    val score: Double,
    val confidence: Double,
    val reasons: List<String>
) {
    companion object {
        val ZERO = EstimatedEffort(
            minMinutes = 0,
            maxMinutes = 0,
            score = 0.0,
            confidence = 0.0,
            reasons = emptyList()
        )
    }
}
