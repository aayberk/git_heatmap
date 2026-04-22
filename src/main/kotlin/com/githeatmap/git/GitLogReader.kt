package com.githeatmap.git

import com.githeatmap.model.CommitEvent

class GitLogReader {

    fun readCommits(repoRootPath: String, limit: Int? = null, branch: String? = null): List<CommitEvent> {
        val args = mutableListOf(
            "log",
            "--pretty=format:%H|%ct|%an|%s",
            "--numstat"
        )
        if (limit != null) {
            args += listOf("-n", limit.toString())
        }
        if (!branch.isNullOrBlank()) {
            args += branch
        }
        val output = GitCommandRunner.run(repoRootPath, *args.toTypedArray())

        return GitParsingSupport.parseLogOutput(output)
    }

    fun readCommitsSince(
        repoRootPath: String,
        sinceHash: String,
        branch: String? = null,
        limit: Int? = null
    ): List<CommitEvent> {
        val range = if (branch.isNullOrBlank()) "$sinceHash..HEAD" else "$sinceHash..$branch"
        val args = mutableListOf(
            "log",
            "--pretty=format:%H|%ct|%an|%s",
            "--numstat",
            range
        )
        if (limit != null) {
            args.addAll(3, listOf("-n", limit.toString()))
        }
        val output = GitCommandRunner.run(repoRootPath, *args.toTypedArray())

        return GitParsingSupport.parseLogOutput(output)
    }

    fun readCommitsInRange(
        repoRootPath: String,
        baseRef: String,
        targetRef: String
    ): List<CommitEvent> {
        val output = GitCommandRunner.run(
            repoRootPath,
            "log",
            "--pretty=format:%H|%ct|%an|%s",
            "--numstat",
            "$baseRef..$targetRef"
        )

        return GitParsingSupport.parseLogOutput(output)
    }
}
