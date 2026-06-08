package com.githeatmap.ui

internal object TokenPricing {
    fun claudeCostUsd(
        model: String?,
        inputTokens: Long,
        cacheCreation5mTokens: Long,
        cacheCreation1hTokens: Long,
        cacheCreationUnclassifiedTokens: Long,
        cacheReadTokens: Long,
        outputTokens: Long,
        inferenceGeo: String?
    ): TokenPriceEstimate {
        val match = ClaudePricing.forModel(model) ?: return TokenPriceEstimate.ZERO
        val pricing = match.pricing
        return TokenPriceEstimate(
            usd = pricing.costUsd(
                inputTokens = inputTokens,
                cacheCreation5mTokens = cacheCreation5mTokens + cacheCreationUnclassifiedTokens,
                cacheCreation1hTokens = cacheCreation1hTokens,
                cacheReadTokens = cacheReadTokens,
                outputTokens = outputTokens
            ),
            usesFallbackPricing = match.usesFallbackPricing
        )
    }

    fun openAiCostUsd(
        model: String?,
        inputTokens: Long,
        cachedInputTokens: Long,
        outputTokens: Long
    ): TokenPriceEstimate {
        val match = OpenAiPricing.forModel(model) ?: return TokenPriceEstimate.ZERO
        val pricing = match.pricing
        val cached = cachedInputTokens.coerceAtMost(inputTokens)
        val billableInput = (inputTokens - cached).coerceAtLeast(0)
        return TokenPriceEstimate(
            usd = pricing.costUsd(
                inputTokens = billableInput,
                cachedInputTokens = cached,
                outputTokens = outputTokens
            ),
            usesFallbackPricing = match.usesFallbackPricing
        )
    }
}

internal data class TokenPriceEstimate(
    val usd: Double,
    val usesFallbackPricing: Boolean
) {
    companion object {
        val ZERO = TokenPriceEstimate(usd = 0.0, usesFallbackPricing = false)
    }
}

private data class PricingMatch<T>(
    val pricing: T,
    val usesFallbackPricing: Boolean
)

private data class ClaudePricing(
    val inputPerMillion: Double,
    val cacheCreation5mPerMillion: Double,
    val cacheCreation1hPerMillion: Double,
    val cacheReadPerMillion: Double,
    val outputPerMillion: Double,
    val supportsDataResidencyMultiplier: Boolean = false
) {
    fun costUsd(
        inputTokens: Long,
        cacheCreation5mTokens: Long,
        cacheCreation1hTokens: Long,
        cacheReadTokens: Long,
        outputTokens: Long
    ): Double {
        return inputTokens.cost(inputPerMillion) +
            (cacheCreation5mTokens + cacheCreation1hTokens).cost(cacheCreation5mPerMillion) +
            cacheReadTokens.cost(cacheReadPerMillion) +
            outputTokens.cost(outputPerMillion)
    }

    companion object {
        fun forModel(model: String?): PricingMatch<ClaudePricing>? {
            val normalized = model?.lowercase().orEmpty()
            return when {
                normalized.contains("opus-4-8") -> OPUS_45_PLUS.exact()
                normalized.contains("opus-4-7") -> OPUS_45_PLUS.exact()
                normalized.contains("opus-4-6") -> OPUS_45_PLUS.exact()
                normalized.contains("opus-4-5") -> OPUS_45_PLUS.exact()
                Regex("""opus-4-[5-9]""").containsMatchIn(normalized) -> OPUS_45_PLUS.fallback()
                normalized.contains("opus-4-1") -> OPUS_4_LEGACY.exact()
                normalized.contains("opus-4") -> OPUS_4_LEGACY.exact()
                normalized.contains("opus") -> OPUS_45_PLUS.fallback()
                normalized.contains("sonnet-4") -> SONNET_4.exact()
                normalized.contains("sonnet") -> SONNET_4.fallback()
                normalized.contains("haiku-4-5") -> HAIKU_45.exact()
                normalized.contains("haiku-3-5") -> HAIKU_35.exact()
                normalized.contains("haiku") -> HAIKU_45.fallback()
                else -> null
            }
        }

        private val OPUS_45_PLUS = ClaudePricing(
            inputPerMillion = 5.0,
            cacheCreation5mPerMillion = 6.25,
            cacheCreation1hPerMillion = 10.0,
            cacheReadPerMillion = 0.50,
            outputPerMillion = 25.0,
            supportsDataResidencyMultiplier = true
        )
        private val OPUS_4_LEGACY = ClaudePricing(
            inputPerMillion = 15.0,
            cacheCreation5mPerMillion = 18.75,
            cacheCreation1hPerMillion = 30.0,
            cacheReadPerMillion = 1.50,
            outputPerMillion = 75.0
        )
        private val SONNET_4 = ClaudePricing(
            inputPerMillion = 3.0,
            cacheCreation5mPerMillion = 3.75,
            cacheCreation1hPerMillion = 6.0,
            cacheReadPerMillion = 0.30,
            outputPerMillion = 15.0,
            supportsDataResidencyMultiplier = true
        )
        private val HAIKU_45 = ClaudePricing(
            inputPerMillion = 1.0,
            cacheCreation5mPerMillion = 1.25,
            cacheCreation1hPerMillion = 2.0,
            cacheReadPerMillion = 0.10,
            outputPerMillion = 5.0,
            supportsDataResidencyMultiplier = true
        )
        private val HAIKU_35 = ClaudePricing(
            inputPerMillion = 0.80,
            cacheCreation5mPerMillion = 1.0,
            cacheCreation1hPerMillion = 1.60,
            cacheReadPerMillion = 0.08,
            outputPerMillion = 4.0
        )
    }
}

private data class OpenAiPricing(
    val inputPerMillion: Double,
    val cachedInputPerMillion: Double,
    val outputPerMillion: Double
) {
    fun costUsd(inputTokens: Long, cachedInputTokens: Long, outputTokens: Long): Double {
        return inputTokens.cost(inputPerMillion) +
            cachedInputTokens.cost(cachedInputPerMillion) +
            outputTokens.cost(outputPerMillion)
    }

    companion object {
        fun forModel(model: String?): PricingMatch<OpenAiPricing>? {
            val normalized = model?.lowercase().orEmpty()
            return when {
                normalized.isBlank() -> CODEX_USAGE.fallback()
                normalized.startsWith("gpt-5.5") -> GPT_55.exact()
                normalized.startsWith("gpt-5.4") -> GPT_54.exact()
                normalized.startsWith("gpt-5.3") -> GPT_52.exact()
                normalized.startsWith("gpt-5.2") -> GPT_52.exact()
                normalized.startsWith("gpt-5.1-codex") -> CODEX_USAGE.exact()
                normalized.startsWith("gpt-5-codex") -> CODEX_USAGE.exact()
                normalized == "gpt-5" -> CODEX_USAGE.fallback()
                normalized.startsWith("gpt-5") -> CODEX_USAGE.fallback()
                normalized.startsWith("gpt-6") -> CODEX_USAGE.fallback()
                normalized.contains("codex") -> CODEX_USAGE.fallback()
                else -> null
            }
        }

        private val CODEX_USAGE = OpenAiPricing(
            inputPerMillion = 1.25,
            cachedInputPerMillion = 0.125,
            outputPerMillion = 10.0
        )
        private val GPT_55 = OpenAiPricing(
            inputPerMillion = 5.0,
            cachedInputPerMillion = 0.50,
            outputPerMillion = 30.0
        )
        private val GPT_54 = OpenAiPricing(
            inputPerMillion = 2.50,
            cachedInputPerMillion = 0.25,
            outputPerMillion = 15.0
        )
        private val GPT_52 = OpenAiPricing(
            inputPerMillion = 1.75,
            cachedInputPerMillion = 0.175,
            outputPerMillion = 14.0
        )
    }
}

private fun <T> T.exact(): PricingMatch<T> = PricingMatch(this, usesFallbackPricing = false)

private fun <T> T.fallback(): PricingMatch<T> = PricingMatch(this, usesFallbackPricing = true)

private fun Long.cost(pricePerMillion: Double): Double = this * pricePerMillion / 1_000_000.0
