package com.githeatmap.git

import java.io.File
import kotlin.concurrent.thread

internal object GitCommandRunner {

    fun run(projectPath: String, vararg args: String): List<String> {
        val process = ProcessBuilder(listOf("git", *args))
            .directory(File(projectPath))
            .start()

        var stderr = ""
        val stderrReader = thread(start = true, name = "git-stderr-reader") {
            stderr = process.errorStream.bufferedReader().use { it.readText() }
        }

        val stdout = process.inputStream.bufferedReader().use { it.readLines() }
        val exitCode = process.waitFor()
        stderrReader.join()

        if (exitCode != 0) {
            val command = listOf("git", *args).joinToString(" ")
            val errorMessage = stderr.trim().ifEmpty { "unknown git error" }
            throw IllegalStateException("Git command failed ($exitCode): $command\n$errorMessage")
        }

        return stdout
    }
}
