package com.githeatmap.ui

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

internal object TokenUsageSupport {
    fun readClaudeUsage(
        roots: List<File>,
        zoneId: ZoneId = ZoneId.systemDefault(),
        maxFiles: Int = DEFAULT_MAX_FILES
    ): TokenUsageResult {
        val files = roots.flatMap { root ->
            val projects = if (root.name == "projects") root else root.resolve("projects")
            projects.jsonlFiles(maxFiles)
        }.distinctBy { it.safeCanonicalPath() }
        val usageByMonth = linkedMapOf<YearMonth, TokenBreakdown>()
        val seenUsageIds = mutableSetOf<String>()
        files.forEach { file ->
            file.forEachLineSafely { line ->
                if (!line.contains(""""usage"""")) return@forEachLineSafely
                val json = jsonObject(line) ?: return@forEachLineSafely
                val message = json.objectMember("message")
                if (!isClaudeAssistantUsage(json, message)) return@forEachLineSafely
                val usage = message?.objectMember("usage") ?: json.objectMember("usage") ?: return@forEachLineSafely
                val usageId = message?.stringMember("id") ?: json.stringMember("requestId")
                if (usageId != null && !seenUsageIds.add(usageId)) return@forEachLineSafely
                val month = monthForJson(json, file, zoneId)
                val cacheCreationTokens = usage.longMember("cache_creation_input_tokens")
                val cacheCreation = usage.objectMember("cache_creation")
                val cacheCreation5mTokens = cacheCreation?.longMember("ephemeral_5m_input_tokens") ?: 0
                val cacheCreation1hTokens = cacheCreation?.longMember("ephemeral_1h_input_tokens") ?: 0
                val cacheCreationUnclassifiedTokens = (
                    cacheCreationTokens -
                        cacheCreation5mTokens -
                        cacheCreation1hTokens
                    ).coerceAtLeast(0)
                val inputTokens = usage.longMember("input_tokens")
                val cacheReadTokens = usage.longMember("cache_read_input_tokens")
                val outputTokens = usage.longMember("output_tokens")
                val price = TokenPricing.claudeCostUsd(
                    model = message?.stringMember("model") ?: json.stringMember("model"),
                    inputTokens = inputTokens,
                    cacheCreation5mTokens = cacheCreation5mTokens,
                    cacheCreation1hTokens = cacheCreation1hTokens,
                    cacheCreationUnclassifiedTokens = cacheCreationUnclassifiedTokens,
                    cacheReadTokens = cacheReadTokens,
                    outputTokens = outputTokens,
                    inferenceGeo = usage.stringMember("inference_geo")
                )
                usageByMonth[month] = usageByMonth.getOrDefault(month, TokenBreakdown.ZERO) + TokenBreakdown(
                    inputTokens = inputTokens,
                    cacheCreationTokens = cacheCreationTokens,
                    cacheReadTokens = cacheReadTokens,
                    outputTokens = outputTokens,
                    priceUsd = price.usd,
                    fallbackPriceCount = if (price.usesFallbackPricing) 1 else 0
                )
            }
        }
        return TokenUsageResult(usageByMonth.toRows(), files.size)
    }

    fun readCodexUsage(
        roots: List<File>,
        zoneId: ZoneId = ZoneId.systemDefault(),
        maxFiles: Int = DEFAULT_MAX_FILES
    ): TokenUsageResult {
        val files = roots.flatMap { root ->
            val sessions = root.resolve("sessions")
            if (sessions.isDirectory) sessions.jsonlFiles(maxFiles) else root.jsonlFiles(maxFiles)
        }.distinctBy { it.safeCanonicalPath() }
        val usageByMonth = linkedMapOf<YearMonth, TokenBreakdown>()
        files.forEach { file ->
            var currentModel: String? = null
            file.forEachLineSafely { line ->
                val json = jsonObject(line) ?: return@forEachLineSafely
                val payload = json.objectMember("payload") ?: return@forEachLineSafely
                if (json.stringMember("type") == "turn_context") {
                    currentModel = payload.stringMember("model")
                        ?: payload.objectMember("collaboration_mode")?.stringMember("model")
                        ?: currentModel
                    return@forEachLineSafely
                }
                if (!line.contains(""""token_count"""") || !line.contains(""""last_token_usage"""")) {
                    return@forEachLineSafely
                }
                if (payload.stringMember("type") != "token_count") return@forEachLineSafely
                val usage = payload
                    .objectMember("info")
                    ?.objectMember("last_token_usage")
                    ?: return@forEachLineSafely
                val input = usage.longMember("input_tokens")
                val cachedInput = usage.longMember("cached_input_tokens")
                val output = usage.longMember("output_tokens")
                val price = TokenPricing.openAiCostUsd(
                    model = currentModel,
                    inputTokens = input,
                    cachedInputTokens = cachedInput,
                    outputTokens = output
                )
                val currentUsage = TokenBreakdown(
                    inputTokens = input,
                    cacheReadTokens = cachedInput,
                    outputTokens = output,
                    reasoningTokens = usage.longMember("reasoning_output_tokens"),
                    explicitTotalTokens = input + output,
                    priceUsd = price.usd,
                    fallbackPriceCount = if (price.usesFallbackPricing) 1 else 0
                )
                if (currentUsage.totalTokens <= 0) return@forEachLineSafely

                val month = monthForJson(json, file, zoneId)
                usageByMonth[month] = usageByMonth.getOrDefault(month, TokenBreakdown.ZERO) + currentUsage
            }
        }
        return TokenUsageResult(usageByMonth.toRows(), files.size)
    }

    fun numberValue(content: String, key: String): Long {
        return Regex(""""$key"\s*:\s*([0-9]+)""", RegexOption.IGNORE_CASE)
            .find(content)
            ?.groupValues
            ?.getOrNull(1)
            ?.toLongOrNull()
            ?: 0
    }

    fun stringValue(content: String, key: String): String? {
        return Regex(""""$key"\s*:\s*"([^"]+)"""", RegexOption.IGNORE_CASE)
            .find(content)
            ?.groupValues
            ?.getOrNull(1)
    }

    fun claudeUsageIdentity(line: String): String? {
        val messageId = objectBlock(line, "message")?.let { block -> stringValue(block, "id") }
        return messageId ?: stringValue(line, "requestId")
    }

    fun isClaudeAssistantUsageLine(line: String): Boolean {
        return stringValue(line, "type") == "assistant" ||
            objectBlock(line, "message")?.let { block -> stringValue(block, "role") == "assistant" } == true
    }

    fun claudeUsageBlock(line: String): String? {
        val messageUsage = objectBlock(line, "message")?.let { block -> objectBlock(block, "usage") }
        return messageUsage ?: objectBlock(line, "usage")
    }

    fun isClaudeAssistantUsage(json: JsonObject, message: JsonObject?): Boolean {
        return json.stringMember("type") == "assistant" || message?.stringMember("role") == "assistant"
    }

    fun monthForLine(line: String, file: File, zoneId: ZoneId): YearMonth {
        val timestamp = stringValue(line, "timestamp") ?: stringValue(line, "time") ?: stringValue(line, "createdAt")
        return monthForTimestamp(timestamp, zoneId) ?: monthForFile(file, zoneId)
    }

    fun monthForJson(json: JsonObject, file: File, zoneId: ZoneId): YearMonth {
        val timestamp = json.stringMember("timestamp") ?: json.stringMember("time") ?: json.stringMember("createdAt")
        return monthForTimestamp(timestamp, zoneId) ?: monthForFile(file, zoneId)
    }

    fun objectBlock(content: String, key: String): String? = objectBlocks(content, key).firstOrNull()

    fun objectBlocks(content: String, key: String): List<String> {
        val blocks = mutableListOf<String>()
        var searchFrom = 0
        while (searchFrom < content.length) {
            val start = content.indexOf(""""$key"""", searchFrom)
            if (start < 0) break
            val open = content.indexOf('{', start)
            if (open < 0) break
            var depth = 0
            var isInsideString = false
            var isEscaped = false
            var found = false
            for (index in open until content.length) {
                val char = content[index]
                if (isEscaped) {
                    isEscaped = false
                    continue
                }
                if (char == '\\' && isInsideString) {
                    isEscaped = true
                    continue
                }
                if (char == '"') {
                    isInsideString = !isInsideString
                    continue
                }
                if (isInsideString) continue

                when (char) {
                    '{' -> depth += 1
                    '}' -> {
                        depth -= 1
                        if (depth == 0) {
                            blocks += content.substring(open, index + 1)
                            searchFrom = index + 1
                            found = true
                            break
                        }
                    }
                }
            }
            if (!found) break
        }
        return blocks
    }

    fun Map<YearMonth, TokenBreakdown>.toRows(): List<TokenUsageRow> {
        if (isEmpty()) return listOf(TokenBreakdown.ZERO.toRow("", isTotal = true))
        val monthlyRows = entries
            .sortedBy { it.key }
            .map { entry -> entry.value.toRow(month = entry.key.toString()) }
        val total = values.fold(TokenBreakdown.ZERO) { aggregate, usage -> aggregate + usage }
        return monthlyRows + total.toRow("Total", isTotal = true)
    }

    private fun monthForTimestamp(timestamp: String?, zoneId: ZoneId): YearMonth? {
        if (timestamp.isNullOrBlank()) return null
        return runCatching {
            YearMonth.from(Instant.parse(timestamp).atZone(zoneId))
        }.getOrNull()
    }

    private fun monthForFile(file: File, zoneId: ZoneId): YearMonth {
        return YearMonth.from(Instant.ofEpochMilli(file.lastModified()).atZone(zoneId))
    }

    private fun File.jsonlFiles(maxFiles: Int): List<File> {
        return walkFiles(maxFiles)
            .filter { file -> file.extension.equals("jsonl", ignoreCase = true) }
    }

    private fun File.walkFiles(maxFiles: Int): List<File> {
        if (!isDirectory) return emptyList()
        return walkTopDown()
            .filter { it.isFile }
            .take(maxFiles)
            .toList()
    }

    private fun File.forEachLineSafely(action: (String) -> Unit) {
        runCatching {
            bufferedReader().useLines { lines -> lines.forEach(action) }
        }
    }

    private fun File.safeCanonicalPath(): String = runCatching { canonicalPath }.getOrDefault(absolutePath)

    private const val DEFAULT_MAX_FILES = 20_000

    private fun jsonObject(content: String): JsonObject? {
        return runCatching { JsonParser.parseString(content).asJsonObject }.getOrNull()
    }

    private fun JsonObject.objectMember(key: String): JsonObject? {
        val value = get(key) ?: return null
        return if (value.isJsonObject) value.asJsonObject else null
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
}

internal data class TokenUsageResult(
    val rows: List<TokenUsageRow>,
    val filesScanned: Int
)

internal data class TokenBreakdown(
    val inputTokens: Long = 0,
    val cacheCreationTokens: Long = 0,
    val cacheReadTokens: Long = 0,
    val outputTokens: Long = 0,
    val reasoningTokens: Long = 0,
    val explicitTotalTokens: Long? = null,
    val priceUsd: Double = 0.0,
    val fallbackPriceCount: Int = 0
) {
    val totalTokens: Long
        get() = explicitTotalTokens ?: (inputTokens + cacheCreationTokens + cacheReadTokens + outputTokens)

    operator fun plus(other: TokenBreakdown): TokenBreakdown {
        return TokenBreakdown(
            inputTokens = inputTokens + other.inputTokens,
            cacheCreationTokens = cacheCreationTokens + other.cacheCreationTokens,
            cacheReadTokens = cacheReadTokens + other.cacheReadTokens,
            outputTokens = outputTokens + other.outputTokens,
            reasoningTokens = reasoningTokens + other.reasoningTokens,
            explicitTotalTokens = totalTokens + other.totalTokens,
            priceUsd = priceUsd + other.priceUsd,
            fallbackPriceCount = fallbackPriceCount + other.fallbackPriceCount
        )
    }

    fun deltaFrom(previous: TokenBreakdown): TokenBreakdown {
        val totalDelta = totalTokens - previous.totalTokens
        if (totalDelta < 0) return this

        return TokenBreakdown(
            inputTokens = (inputTokens - previous.inputTokens).coerceAtLeast(0),
            cacheCreationTokens = (cacheCreationTokens - previous.cacheCreationTokens).coerceAtLeast(0),
            cacheReadTokens = (cacheReadTokens - previous.cacheReadTokens).coerceAtLeast(0),
            outputTokens = (outputTokens - previous.outputTokens).coerceAtLeast(0),
            reasoningTokens = (reasoningTokens - previous.reasoningTokens).coerceAtLeast(0),
            explicitTotalTokens = totalDelta,
            priceUsd = (priceUsd - previous.priceUsd).coerceAtLeast(0.0),
            fallbackPriceCount = (fallbackPriceCount - previous.fallbackPriceCount).coerceAtLeast(0)
        )
    }

    fun toRow(month: String, isTotal: Boolean = false): TokenUsageRow {
        return TokenUsageRow(
            month = month,
            inputTokens = inputTokens,
            cacheCreationTokens = cacheCreationTokens,
            cacheReadTokens = cacheReadTokens,
            outputTokens = outputTokens,
            reasoningTokens = reasoningTokens,
            totalTokens = totalTokens,
            priceUsd = priceUsd,
            fallbackPriceCount = fallbackPriceCount,
            isTotal = isTotal
        )
    }

    companion object {
        val ZERO = TokenBreakdown()
    }
}

internal data class TokenUsageRow(
    val month: String,
    val inputTokens: Long,
    val cacheCreationTokens: Long,
    val cacheReadTokens: Long,
    val outputTokens: Long,
    val reasoningTokens: Long,
    val totalTokens: Long,
    val priceUsd: Double,
    val fallbackPriceCount: Int,
    val isTotal: Boolean
)
