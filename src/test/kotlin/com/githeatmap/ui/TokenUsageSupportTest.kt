package com.githeatmap.ui

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

class TokenUsageSupportTest {
    @Test
    fun `codex usage can be filtered by repository root`(@TempDir tempDir: File) {
        val repoA = tempDir.resolve("repo-a").apply { mkdirs() }
        val repoB = tempDir.resolve("repo-b").apply { mkdirs() }
        val sessions = tempDir.resolve("codex/sessions").apply { mkdirs() }
        sessions.resolve("usage.jsonl").writeText(
            """
            {"timestamp":"2026-01-01T00:00:00Z","type":"session_meta","payload":{"cwd":"${repoA.absolutePath.jsonEscaped()}"}}
            {"timestamp":"2026-01-01T00:01:00Z","type":"event_msg","payload":{"type":"token_count","info":{"last_token_usage":{"input_tokens":100,"cached_input_tokens":10,"output_tokens":5,"reasoning_output_tokens":2}}}}
            {"timestamp":"2026-01-01T00:02:00Z","type":"turn_context","payload":{"cwd":"${repoB.absolutePath.jsonEscaped()}","model":"gpt-5-codex"}}
            {"timestamp":"2026-01-01T00:03:00Z","type":"event_msg","payload":{"type":"token_count","info":{"last_token_usage":{"input_tokens":200,"cached_input_tokens":20,"output_tokens":10,"reasoning_output_tokens":4}}}}
            """.trimIndent()
        )

        val result = TokenUsageSupport.readCodexUsage(
            roots = listOf(tempDir.resolve("codex")),
            repositoryRoot = repoA
        )
        val row = result.rows.first { it.month == "2026-01" }

        assertEquals(100, row.inputTokens)
        assertEquals(10, row.cacheReadTokens)
        assertEquals(5, row.outputTokens)
        assertEquals(105, row.totalTokens)
    }

    @Test
    fun `claude repository filter supports hyphenated project names`(@TempDir tempDir: File) {
        val repo = tempDir.resolve("pmp-cloud").apply { mkdirs() }
        val sibling = tempDir.resolve("pmp-cloud2").apply { mkdirs() }
        val claudeRoot = tempDir.resolve("claude")
        val repoProject = claudeRoot.resolve("projects/${repo.canonicalPath.toClaudeProjectKey()}").apply { mkdirs() }
        val siblingProject = claudeRoot.resolve("projects/${sibling.canonicalPath.toClaudeProjectKey()}").apply { mkdirs() }
        repoProject.resolve("repo.jsonl").writeText(claudeUsageLine("repo-usage", inputTokens = 100))
        siblingProject.resolve("sibling.jsonl").writeText(claudeUsageLine("sibling-usage", inputTokens = 300))

        val result = TokenUsageSupport.readClaudeUsage(
            roots = listOf(claudeRoot),
            repositoryRoot = repo
        )
        val row = result.rows.first { it.month == "2026-01" }

        assertEquals(100, row.inputTokens)
        assertEquals(100, row.totalTokens)
    }

    @Test
    fun `claude repository filter includes historical project name variants`(@TempDir tempDir: File) {
        val repo = tempDir.resolve("pmp-cloud").apply { mkdirs() }
        val historicalVariant = tempDir.resolve("pmp-cloud-donusum").apply { mkdirs() }
        val unrelated = tempDir.resolve("pmpweb").apply { mkdirs() }
        val claudeRoot = tempDir.resolve("claude")
        val repoProject = claudeRoot.resolve("projects/${repo.canonicalPath.toClaudeProjectKey()}").apply { mkdirs() }
        val historicalProject = claudeRoot.resolve("projects/${historicalVariant.canonicalPath.toClaudeProjectKey()}").apply { mkdirs() }
        val unrelatedProject = claudeRoot.resolve("projects/${unrelated.canonicalPath.toClaudeProjectKey()}").apply { mkdirs() }
        repoProject.resolve("repo.jsonl").writeText(claudeUsageLine("repo-usage", inputTokens = 100))
        historicalProject.resolve("historical.jsonl").writeText(claudeUsageLine("historical-usage", inputTokens = 300))
        unrelatedProject.resolve("unrelated.jsonl").writeText(claudeUsageLine("unrelated-usage", inputTokens = 900))

        val result = TokenUsageSupport.readClaudeUsage(
            roots = listOf(claudeRoot),
            repositoryRoot = repo
        )
        val row = result.rows.first { it.month == "2026-01" }

        assertEquals(400, row.inputTokens)
        assertEquals(400, row.totalTokens)
    }

    @Test
    fun `claude usage can be grouped daily`(@TempDir tempDir: File) {
        val claudeRoot = tempDir.resolve("claude")
        val project = claudeRoot.resolve("projects/${tempDir.resolve("repo").canonicalPath.toClaudeProjectKey()}").apply { mkdirs() }
        project.resolve("daily.jsonl").writeText(
            """
            ${claudeUsageLine("day-1-a", inputTokens = 100, timestamp = "2026-01-01T10:00:00Z")}
            ${claudeUsageLine("day-1-b", inputTokens = 200, timestamp = "2026-01-01T11:00:00Z")}
            ${claudeUsageLine("day-2", inputTokens = 300, timestamp = "2026-01-02T10:00:00Z")}
            """.trimIndent()
        )

        val result = TokenUsageSupport.readClaudeUsage(
            roots = listOf(claudeRoot),
            period = TokenUsagePeriod.Daily
        )

        assertEquals(300, result.rows.first { it.month == "2026-01-01" }.inputTokens)
        assertEquals(300, result.rows.first { it.month == "2026-01-02" }.inputTokens)
        assertEquals(600, result.rows.first { it.isTotal }.inputTokens)
    }

    @Test
    fun `claude usage remains grouped monthly by default`(@TempDir tempDir: File) {
        val claudeRoot = tempDir.resolve("claude")
        val project = claudeRoot.resolve("projects/${tempDir.resolve("repo").canonicalPath.toClaudeProjectKey()}").apply { mkdirs() }
        project.resolve("monthly.jsonl").writeText(
            """
            ${claudeUsageLine("jan", inputTokens = 100, timestamp = "2026-01-01T10:00:00Z")}
            ${claudeUsageLine("feb", inputTokens = 300, timestamp = "2026-02-01T10:00:00Z")}
            """.trimIndent()
        )

        val result = TokenUsageSupport.readClaudeUsage(roots = listOf(claudeRoot))

        assertEquals(100, result.rows.first { it.month == "2026-01" }.inputTokens)
        assertEquals(300, result.rows.first { it.month == "2026-02" }.inputTokens)
    }

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

    private fun String.jsonEscaped(): String {
        return replace("\\", "\\\\").replace("\"", "\\\"")
    }

    private fun String.toClaudeProjectKey(): String {
        return replace("/", "-")
    }

    private fun claudeUsageLine(
        id: String,
        inputTokens: Long,
        timestamp: String = "2026-01-01T00:00:00Z"
    ): String {
        return """
            {"timestamp":"$timestamp","type":"assistant","message":{"id":"$id","role":"assistant","model":"claude-haiku-4-5-20251001","usage":{"input_tokens":$inputTokens,"cache_creation_input_tokens":0,"cache_read_input_tokens":0,"output_tokens":0}}}
        """.trimIndent()
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
