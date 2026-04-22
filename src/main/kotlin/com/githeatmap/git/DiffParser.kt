package com.githeatmap.git

import com.githeatmap.model.FileChange

class DiffParser {

    fun parseCommitDiff(repoRootPath: String, commitHash: String): List<FileChange> {
        val output = GitCommandRunner.run(repoRootPath, "diff", "--numstat", "$commitHash^", commitHash)

        return GitParsingSupport.parseDiffOutput(output)
    }

    fun parseBranchDiff(repoRootPath: String, baseBranch: String, targetBranch: String): List<FileChange> {
        val output = GitCommandRunner.run(repoRootPath, "diff", "--numstat", "$baseBranch...$targetBranch")

        return GitParsingSupport.parseDiffOutput(output)
            .distinctBy { it.path }
    }
}
