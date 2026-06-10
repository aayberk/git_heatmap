package com.githeatmap.ui

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.io.IOException
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

class TokenUsageSupportTest {
    @Test
    fun `new model names use latest known pricing fallback`() {
        val codex = TokenPricing.openAiCostUsd(
            model = "gpt-5.6-codex",
            inputTokens = 1_000_000,
            cachedInputTokens = 0,
            outputTokens = 1_000_000
        )
        val claude = TokenPricing.claudeCostUsd(
            model = "claude-sonnet-5-20260601",
            inputTokens = 1_000_000,
            cacheCreation5mTokens = 0,
            cacheCreation1hTokens = 0,
            cacheCreationUnclassifiedTokens = 0,
            cacheReadTokens = 0,
            outputTokens = 1_000_000,
            inferenceGeo = null
        )

        assertTrue(codex.usesFallbackPricing, "Codex fallback marker")
        assertTrue(codex.usd > 0.0, "Codex fallback price")
        assertTrue(claude.usesFallbackPricing, "Claude fallback marker")
        assertTrue(claude.usd > 0.0, "Claude fallback price")
    }

    @Test
    fun `claude fable and mythos 5 pricing is supported`() {
        val fable = TokenPricing.claudeCostUsd(
            model = "claude-fable-5-20260601",
            inputTokens = 1_000_000,
            cacheCreation5mTokens = 1_000_000,
            cacheCreation1hTokens = 0,
            cacheCreationUnclassifiedTokens = 0,
            cacheReadTokens = 1_000_000,
            outputTokens = 1_000_000,
            inferenceGeo = null
        )
        val mythos = TokenPricing.claudeCostUsd(
            model = "claude-mythos-5-20260601",
            inputTokens = 1_000_000,
            cacheCreation5mTokens = 1_000_000,
            cacheCreation1hTokens = 0,
            cacheCreationUnclassifiedTokens = 0,
            cacheReadTokens = 1_000_000,
            outputTokens = 1_000_000,
            inferenceGeo = null
        )

        assertEquals(73.5, fable.usd, 0.000001, "Fable 5 cost")
        assertEquals(73.5, mythos.usd, 0.000001, "Mythos 5 cost")
    }

    @Test
    fun `claude monthly totals match ccusage`() {
        val home = File(System.getProperty("user.home"))
        val roots = listOf(
            home.resolve(".config/claude"),
            home.resolve(".claude")
        ).filter { it.isDirectory }
        assumeTrue(roots.isNotEmpty(), "Claude local usage directory is not available")

        val expected = readMonthlyUsage("ccusage") { month -> month }
            .withoutCurrentMonth()
        assumeTrue(expected.isNotEmpty(), "ccusage monthly output is not available")

        val result = TokenUsageSupport.readClaudeUsage(roots)
        val rowsByMonth = result.rows.associateBy { it.month }

        expected.forEach { (month, expectedUsage) ->
            val actual = rowsByMonth.getValue(month)
            assertEquals(expectedUsage.inputTokens, actual.inputTokens, "$month input")
            assertEquals(expectedUsage.outputTokens, actual.outputTokens, "$month output")
            assertEquals(expectedUsage.cacheCreationTokens, actual.cacheCreationTokens, "$month cache create")
            assertEquals(expectedUsage.cacheReadTokens, actual.cacheReadTokens, "$month cache read")
            assertEquals(expectedUsage.reasoningTokens, actual.reasoningTokens, "$month reasoning")
            assertEquals(expectedUsage.totalTokens, actual.totalTokens, "$month total")
            assertEquals(expectedUsage.priceUsd, actual.priceUsd, 0.000001, "$month cost")
        }
    }

    @Test
    fun `codex monthly totals match ccusage codex`() {
        val home = File(System.getProperty("user.home"))
        val roots = listOf(home.resolve(".codex")).filter { it.isDirectory }
        assumeTrue(roots.isNotEmpty(), "Codex local usage directory is not available")

        val expected = readMonthlyUsage("ccusage-codex") { month ->
            YearMonth.parse(month, CODEX_MONTH_FORMAT).toString()
        }.withoutCurrentMonth()
        assumeTrue(expected.isNotEmpty(), "ccusage-codex monthly output is not available")

        val result = TokenUsageSupport.readCodexUsage(roots)
        val rowsByMonth = result.rows.associateBy { it.month }

        expected.forEach { (month, expectedUsage) ->
            val actual = rowsByMonth.getValue(month)
            assertEquals(expectedUsage.inputTokens, actual.inputTokens, "$month input")
            assertEquals(expectedUsage.outputTokens, actual.outputTokens, "$month output")
            assertEquals(expectedUsage.cacheReadTokens, actual.cacheReadTokens, "$month cache read")
            assertEquals(expectedUsage.reasoningTokens, actual.reasoningTokens, "$month reasoning")
            assertEquals(expectedUsage.totalTokens, actual.totalTokens, "$month total")
            assertEquals(expectedUsage.priceUsd, actual.priceUsd, 0.000001, "$month cost")
        }
    }

    private fun readMonthlyUsage(command: String, normalizeMonth: JsonObject.(String) -> String): Map<String, ExpectedUsage> {
        val output = runCatching {
            val process = ProcessBuilder(command, "monthly", "--json")
                .redirectErrorStream(true)
                .start()
            val text = process.inputStream.bufferedReader().readText()
            val finished = process.waitFor(60, TimeUnit.SECONDS)
            if (!finished || process.exitValue() != 0) return emptyMap()
            text
        }.recoverCatching { error ->
            if (error is IOException) return emptyMap()
            throw error
        }.getOrThrow()

        val json = runCatching { JsonParser.parseString(output).asJsonObject }.getOrNull() ?: return emptyMap()
        val monthly = json.getAsJsonArray("monthly") ?: return emptyMap()
        return monthly.mapNotNull { element ->
            val item = element.asJsonObject
            val month = item.stringMember("month") ?: return@mapNotNull null
            item.normalizeMonth(month) to ExpectedUsage(
                inputTokens = item.longMember("inputTokens"),
                outputTokens = item.longMember("outputTokens"),
                cacheCreationTokens = item.longMember("cacheCreationTokens"),
                cacheReadTokens = item.longMember("cacheReadTokens") + item.longMember("cachedInputTokens"),
                reasoningTokens = item.longMember("reasoningOutputTokens"),
                totalTokens = item.longMember("totalTokens"),
                priceUsd = item.doubleMember("totalCost").takeIf { it > 0.0 } ?: item.doubleMember("costUSD")
            )
        }.toMap()
    }

    private fun JsonObject.stringMember(key: String): String? {
        val value = get(key) ?: return null
        return if (value.isJsonPrimitive && value.asJsonPrimitive.isString) value.asString else null
    }

    private fun JsonObject.longMember(key: String): Long {
        val value = get(key) ?: return 0
        return runCatching {
            if (value.isJsonPrimitive && value.asJsonPrimitive.isNumber) value.asLong else 0
        }.getOrDefault(0)
    }

    private fun JsonObject.doubleMember(key: String): Double {
        val value = get(key) ?: return 0.0
        return runCatching {
            if (value.isJsonPrimitive && value.asJsonPrimitive.isNumber) value.asDouble else 0.0
        }.getOrDefault(0.0)
    }

    private data class ExpectedUsage(
        val inputTokens: Long,
        val outputTokens: Long,
        val cacheCreationTokens: Long,
        val cacheReadTokens: Long,
        val reasoningTokens: Long,
        val totalTokens: Long,
        val priceUsd: Double
    )

    private fun Map<String, ExpectedUsage>.withoutCurrentMonth(): Map<String, ExpectedUsage> {
        val currentMonth = YearMonth.now().toString()
        return filterKeys { month -> month != currentMonth }
    }

    private companion object {
        val CODEX_MONTH_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM yyyy", Locale.US)
    }
}
