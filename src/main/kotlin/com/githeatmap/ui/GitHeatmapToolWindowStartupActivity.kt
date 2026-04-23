package com.githeatmap.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager

class GitHeatmapToolWindowStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        val toolWindowManager = ToolWindowManager.getInstance(project)
        toolWindowManager.invokeLater {
            if (project.isDisposed) return@invokeLater

            val toolWindow = toolWindowManager.getToolWindow(TOOL_WINDOW_ID)
                ?: toolWindowManager.registerToolWindow(TOOL_WINDOW_ID) {
                    anchor = ToolWindowAnchor.BOTTOM
                    icon = AllIcons.Actions.Search
                    sideTool = true
                    canCloseContent = false
                    shouldBeAvailable = true
                }

            if (toolWindow.contentManager.contentCount == 0) {
                HeatmapToolWindowContentBuilder().createToolWindowContent(project, toolWindow)
            }
        }
    }

    companion object {
        const val TOOL_WINDOW_ID = "Git Heatmap"
    }
}
