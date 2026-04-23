package com.githeatmap.git

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import java.io.File

class GitRootDiscovery(private val project: Project) {

    fun discoverRoots(): List<DiscoveredGitRoot> {
        val projectBasePath = project.basePath ?: return emptyList()
        val projectBaseDir = File(projectBasePath)

        val vcsRoots = ProjectLevelVcsManager.getInstance(project).getAllVcsRoots()
            .map { root -> root.path.path }

        val discoveredPaths = (vcsRoots + fallbackScan(projectBaseDir))
            .distinct()
            .sorted()

        return discoveredPaths.map { path ->
            val rootDir = File(path)
            DiscoveredGitRoot(
                id = path,
                name = rootDir.name.ifBlank { path },
                absolutePath = path,
                relativePath = rootDir.relativeToOrSelf(projectBaseDir).path.ifBlank { "." }
            )
        }
    }

    private fun fallbackScan(projectBaseDir: File): List<String> {
        if (!projectBaseDir.exists()) return emptyList()

        return projectBaseDir.walkTopDown()
            .maxDepth(FALLBACK_SCAN_DEPTH)
            .filter { candidate -> candidate.name == ".git" }
            .mapNotNull { gitMarker -> gitMarker.parentFile?.absolutePath }
            .distinct()
            .toList()
    }

    data class DiscoveredGitRoot(
        val id: String,
        val name: String,
        val absolutePath: String,
        val relativePath: String
    ) {
        val displayName: String
            get() = if (relativePath == ".") name else "$name ($relativePath)"

        override fun toString(): String = displayName
    }

    private companion object {
        private const val FALLBACK_SCAN_DEPTH = 4
    }
}
