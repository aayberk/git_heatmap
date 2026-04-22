package com.githeatmap.git

import com.githeatmap.model.CommitEvent
import com.githeatmap.model.FileChange

internal object GitParsingSupport {
    private val renameBlockPattern = Regex("\\{([^{}]*) => ([^{}]*)\\}")

    fun parseLogOutput(lines: List<String>): List<CommitEvent> {
        val commits = mutableListOf<CommitEvent>()
        var index = 0

        while (index < lines.size) {
            val header = lines[index].split("|")
            if (header.size < 4) {
                index++
                continue
            }

            val hash = header[0]
            val timestamp = header[1].toLongOrNull() ?: 0
            val author = header[2]
            val message = header.subList(3, header.size).joinToString("|")

            val files = mutableListOf<FileChange>()
            index++

            while (index < lines.size && lines[index].contains("\t")) {
                parseDiffLine(lines[index])?.let(files::add)
                index++
            }

            commits.add(CommitEvent(hash, timestamp, author, message, files))
        }

        return commits
    }

    fun parseDiffOutput(lines: List<String>): List<FileChange> = lines.mapNotNull(::parseDiffLine)

    fun parseDiffLine(line: String): FileChange? {
        val parts = line.split("\t")
        if (parts.size < 3) return null

        return FileChange(
            path = normalizePath(parts[2]),
            addedLines = parseNumstatValue(parts[0]),
            deletedLines = parseNumstatValue(parts[1])
        )
    }

    fun normalizePath(path: String): String {
        if (!path.contains("=>")) return path

        var normalized = path
        while (true) {
            val replaced = renameBlockPattern.replace(normalized) { it.groupValues[2] }
            if (replaced == normalized) break
            normalized = replaced
        }

        return normalized.substringAfter(" => ", normalized).trim()
    }

    private fun parseNumstatValue(value: String): Int {
        return if (value == "-") 0 else value.toIntOrNull() ?: 0
    }
}
