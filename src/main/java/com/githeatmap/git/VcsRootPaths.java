package com.githeatmap.git;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsRoot;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

final class VcsRootPaths {
    private VcsRootPaths() {
    }

    static List<String> getAll(@NotNull Project project) {
        VcsRoot[] roots = ProjectLevelVcsManager.getInstance(project).getAllVcsRoots();
        List<String> paths = new ArrayList<>(roots.length);
        for (VcsRoot root : roots) {
            if (root.getPath() != null) {
                paths.add(root.getPath().getPath());
            }
        }
        return paths;
    }
}
