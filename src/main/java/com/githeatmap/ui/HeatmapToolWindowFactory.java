package com.githeatmap.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import org.jetbrains.annotations.NotNull;

public final class HeatmapToolWindowFactory implements ToolWindowFactory {
    private final HeatmapToolWindowContentBuilder delegate = new HeatmapToolWindowContentBuilder();

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        delegate.createToolWindowContent(project, toolWindow);
    }
}
