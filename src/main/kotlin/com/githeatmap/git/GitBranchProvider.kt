package com.githeatmap.git

class GitBranchProvider {

    fun listBranches(repoRootPath: String): BranchChoices {
        val refs = GitCommandRunner.run(
            repoRootPath,
            "for-each-ref",
            "--format=%(refname)",
            "refs/heads",
            "refs/remotes"
        )

        val branches = normalizeRefs(refs)
        val currentBranch = runCatching {
            GitCommandRunner.run(repoRootPath, "rev-parse", "--abbrev-ref", "HEAD").firstOrNull()
        }.getOrNull()
            ?.trim()
            ?.takeUnless { it.isBlank() || it == "HEAD" }

        return BranchChoices(branches, currentBranch)
    }

    internal fun normalizeRefs(refs: List<String>): List<String> {
        return refs.mapNotNull { ref ->
            when {
                ref.startsWith(LOCAL_REF_PREFIX) -> BranchRef(ref.removePrefix(LOCAL_REF_PREFIX), false)
                ref.startsWith(REMOTE_REF_PREFIX) -> {
                    val branch = ref.removePrefix(REMOTE_REF_PREFIX)
                    if (branch.endsWith("/HEAD")) null else BranchRef(branch, true)
                }
                else -> null
            }
        }.distinctBy { it.name }
            .sortedWith(compareBy<BranchRef>({ if (it.isRemote) 1 else 0 }, { it.name.lowercase() }))
            .map { it.name }
    }

    data class BranchChoices(
        val branches: List<String>,
        val currentBranch: String?
    )

    private data class BranchRef(
        val name: String,
        val isRemote: Boolean
    )

    private companion object {
        private const val LOCAL_REF_PREFIX = "refs/heads/"
        private const val REMOTE_REF_PREFIX = "refs/remotes/"
    }
}
