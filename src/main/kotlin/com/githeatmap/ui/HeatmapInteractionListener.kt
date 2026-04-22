package com.githeatmap.ui

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.ToolTipManager

class HeatmapInteractionListener(
    private val panel: HeatmapPanel,
    private val project: Project,
    private val absolutePathResolver: (String) -> String? = { path -> project.basePath?.let { "$it/$path" } },
    private val onSelectionChanged: (String?) -> Unit = {}
) : MouseAdapter() {

    init {
        ToolTipManager.sharedInstance().registerComponent(panel)
    }

    override fun mouseMoved(e: MouseEvent) {
        val file = panel.getFileAt(e.x, e.y)
        panel.selectedPath = file?.metrics?.path
        onSelectionChanged(file?.metrics?.path)
        panel.toolTipText = file?.let {
            "<html>${it.metrics.path}<br/>Heat: ${"%.2f".format(it.heatScore)}<br/>Commits: ${it.metrics.commitCount}</html>"
        }
    }

    override fun mouseClicked(e: MouseEvent) {
        val file = panel.getFileAt(e.x, e.y) ?: return
        panel.selectedPath = file.metrics.path
        onSelectionChanged(file.metrics.path)
        val absolutePath = absolutePathResolver(file.metrics.path) ?: return
        val virtualFile = LocalFileSystem.getInstance().findFileByPath(absolutePath) ?: return

        FileEditorManager.getInstance(project).openFile(virtualFile, true)
    }

    override fun mouseExited(e: MouseEvent) {
        panel.selectedPath = null
        onSelectionChanged(null)
        panel.toolTipText = null
    }
}
