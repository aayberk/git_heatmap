package com.githeatmap.ui

import com.githeatmap.cache.CommitCache
import com.githeatmap.cache.MetricsCache
import com.githeatmap.engine.AuthorStatsCalculator
import com.githeatmap.engine.DateRangeFilter
import com.githeatmap.engine.EffortAggregator
import com.githeatmap.engine.EffortHeuristicEngine
import com.githeatmap.engine.HeatScoreCalculator
import com.githeatmap.engine.MetricEngine
import com.githeatmap.engine.TimeFilter
import com.githeatmap.git.GitBranchProvider
import com.githeatmap.git.DiffParser
import com.githeatmap.git.GitLogReader
import com.githeatmap.git.GitRootDiscovery
import com.githeatmap.model.AuthorFileMetrics
import com.githeatmap.model.AuthorMetrics
import com.githeatmap.model.CommitEvent
import com.githeatmap.model.CommitEffortMetrics
import com.githeatmap.model.EstimatedEffort
import com.githeatmap.model.FileChange
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.JBColor
import com.intellij.ui.ComboboxSpeedSearch
import com.intellij.ui.content.ContentFactory
import java.awt.Dimension
import java.awt.GridLayout
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import javax.swing.Box
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.DefaultComboBoxModel
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JProgressBar
import javax.swing.JScrollPane
import javax.swing.JSpinner
import javax.swing.JTabbedPane
import javax.swing.SpinnerDateModel

class HeatmapToolWindowContentBuilder {
    fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val heatmapPanel = HeatmapPanel()
        val filesPanel = FileListPanel(project)
        val commitFilterLabel = JLabel("All commits")
        lateinit var clearCommitFilterButton: JButton
        lateinit var tabs: JTabbedPane
        val statusLabel = JLabel("Click 'Load' to analyze repository")
        val summaryLabel = JLabel("Commits: 0 | Lines: 0 | Files: 0")
        val overlayLabel = JLabel("No PR overlay")
        val authorFilterLabel = JLabel("All authors")
        val repositoryCombo = JComboBox<GitRootDiscovery.DiscoveredGitRoot>().apply {
            isEnabled = false
        }
        val historyBranchCombo = JComboBox<String>().apply {
            isEnabled = false
            prototypeDisplayValue = "origin/very-long-feature-branch"
            selectedItem = null
            ComboboxSpeedSearch.installOn(this)
        }
        val progressBar = JProgressBar().apply {
            isIndeterminate = true
            isVisible = false
            preferredSize = Dimension(120, 18)
        }
        val zoneId = ZoneId.systemDefault()
        val startDateEnabled = JCheckBox("Start")
        val endDateEnabled = JCheckBox("End")
        val startDateSpinner = createDateSpinner()
        val endDateSpinner = createDateSpinner()
        val gitReader = GitLogReader()
        val branchProvider = GitBranchProvider()
        val diffParser = DiffParser()
        val rootDiscovery = GitRootDiscovery(project)
        val metricEngine = MetricEngine()
        val heatScoreCalculator = HeatScoreCalculator()
        val authorStatsCalculator = AuthorStatsCalculator()
        val effortHeuristicEngine = EffortHeuristicEngine()
        val effortAggregator = EffortAggregator()

        var cachedCommits: List<CommitEvent> = emptyList()
        var overlayPaths: Set<String> = emptySet()
        var overlayScopedCommitIds: Set<String> = emptySet()
        var displayedPathToAbsolutePath: Map<String, String> = emptyMap()
        var activeAuthorFilter: String? = null
        var activeCommitFilterId: String? = null
        var currentAnalysisResult: AnalysisResult? = null
        var discoveredRoots: List<GitRootDiscovery.DiscoveredGitRoot> = emptyList()
        var currentRepositoryRoot: GitRootDiscovery.DiscoveredGitRoot? = null
        var currentHistoryBranch: String? = null
        var currentLoadedBranch: String? = null
        var knownBranches: List<String> = emptyList()
        var updatingRepositoryCombo = false
        var updatingHistoryBranchCombo = false
        val analysisCache = mutableMapOf<AnalysisCacheKey, AnalysisResult>()
        var refreshGeneration = 0
        var loadGeneration = 0
        var overlayGeneration = 0

        lateinit var loadButton: JButton
        lateinit var prButton: JButton
        lateinit var clearOverlayButton: JButton
        lateinit var clearAuthorFilterButton: JButton
        lateinit var resetAnalysisState: () -> Unit
        lateinit var applyAnalysis: (AnalysisResult) -> Unit
        lateinit var updateSummary: (AnalysisResult) -> Unit

        fun styleFilterChip(label: JLabel, isActive: Boolean, activeBorder: JBColor) {
            label.isOpaque = false
            label.border = BorderFactory.createEmptyBorder(3, 4, 3, 4)
            label.foreground = if (isActive) activeBorder else JBColor(0x6B7280, 0xAEB6BF)
        }

        fun updateFilterChips() {
            styleFilterChip(
                overlayLabel,
                isActive = overlayPaths.isNotEmpty(),
                activeBorder = JBColor(0xB45309, 0xF59E0B)
            )
            styleFilterChip(
                authorFilterLabel,
                isActive = activeAuthorFilter != null,
                activeBorder = JBColor(0x1D4ED8, 0x60A5FA)
            )
            styleFilterChip(
                commitFilterLabel,
                isActive = activeCommitFilterId != null,
                activeBorder = JBColor(0x047857, 0x34D399)
            )
        }

        fun currentRepoPath(): String? = currentRepositoryRoot?.takeUnless { it.id == AGGREGATE_SCOPE_ID }?.absolutePath

        fun isAggregateSelection(): Boolean = currentRepositoryRoot?.id == AGGREGATE_SCOPE_ID

        fun currentScopeKey(): String? {
            return if (isAggregateSelection()) {
                discoveredRoots.joinToString("|") { it.id }
            } else {
                currentRepositoryRoot?.id
            }
        }

        fun commitIdentity(commit: CommitEvent): String = "${commit.repoId}::${commit.hash}"

        fun scopePrefix(): String? {
            return when {
                isAggregateSelection() -> "All repositories"
                currentRepositoryRoot != null -> currentRepositoryRoot?.displayName
                else -> null
            }
        }

        fun selectedRoots(): List<GitRootDiscovery.DiscoveredGitRoot> {
            return if (isAggregateSelection()) discoveredRoots else listOfNotNull(currentRepositoryRoot)
        }

        fun displayPrefixFor(root: GitRootDiscovery.DiscoveredGitRoot): String {
            return if (root.relativePath == ".") root.name else root.relativePath
        }

        fun displayPathFor(root: GitRootDiscovery.DiscoveredGitRoot, path: String): String {
            return "${displayPrefixFor(root)}/$path"
        }

        fun configurePathResolver() {
            filesPanel.repositoryRootPath = currentRepoPath()
            filesPanel.absolutePathResolver = { path ->
                displayedPathToAbsolutePath[path] ?: currentRepoPath()?.let { "$it/$path" }
            }
        }

        fun updateRepositorySelector(
            roots: List<GitRootDiscovery.DiscoveredGitRoot>,
            selectedRoot: GitRootDiscovery.DiscoveredGitRoot?
        ) {
            val options = if (roots.size > 1) listOf(aggregateRepositoryOption()) + roots else roots
            updatingRepositoryCombo = true
            repositoryCombo.model = DefaultComboBoxModel(options.toTypedArray())
            repositoryCombo.selectedItem = selectedRoot
            repositoryCombo.isEnabled = options.isNotEmpty()
            updatingRepositoryCombo = false
        }

        fun buildAnalysisResult(
            commits: List<CommitEvent>,
            scoredFiles: List<com.githeatmap.engine.ScoredFile>? = null,
            commitEfforts: List<CommitEffortMetrics> = commits.map { effortHeuristicEngine.estimateCommit(it) }
        ): AnalysisResult {
            val authorFileBreakdown = authorStatsCalculator.calculateFileBreakdown(commits)
            val effortByAuthor = effortAggregator.summarizeAuthorEffort(commitEfforts)
            val effectiveScoredFiles = scoredFiles ?: heatScoreCalculator.calculate(metricEngine.aggregate(commits))
            val authorScoredFiles = commits.groupBy { it.author }
                .mapValues { (_, authorCommits) ->
                    heatScoreCalculator.calculate(metricEngine.aggregate(authorCommits))
                }
            val commitScoredFiles = commits.associate { commit ->
                commitIdentity(commit) to heatScoreCalculator.calculate(metricEngine.aggregate(listOf(commit)))
            }

            return AnalysisResult(
                scoredFiles = effectiveScoredFiles,
                authorStats = authorStatsCalculator.calculate(commits, effortByAuthor),
                authorFileBreakdown = authorFileBreakdown,
                authorScoredFiles = authorScoredFiles,
                commitScoredFiles = commitScoredFiles,
                filteredCommits = commits,
                commitEfforts = commitEfforts,
                totalEffort = effortAggregator.summarizeTotalEffort(commitEfforts),
                commitCount = commits.size,
                totalAdded = commits.sumOf { commit -> commit.files.sumOf { it.addedLines } },
                totalDeleted = commits.sumOf { commit -> commit.files.sumOf { it.deletedLines } }
            )
        }

        fun scopedAnalysisResult(result: AnalysisResult): AnalysisResult {
            if (overlayScopedCommitIds.isEmpty()) return result

            val scopedCommits = result.filteredCommits.filter { commitIdentity(it) in overlayScopedCommitIds }
            val scopedEfforts = result.commitEfforts.filter { effort ->
                "${effort.repoId}::${effort.commitHash}" in overlayScopedCommitIds
            }

            if (scopedCommits.size == result.filteredCommits.size &&
                scopedEfforts.size == result.commitEfforts.size
            ) {
                return result
            }

            return buildAnalysisResult(scopedCommits, commitEfforts = scopedEfforts)
        }

        val commitsPanel = CommitListPanel { commit ->
            val result = currentAnalysisResult?.let(::scopedAnalysisResult) ?: return@CommitListPanel
            activeCommitFilterId = commitIdentity(commit)
            activeAuthorFilter = null
            filesPanel.clearAuthorFilter()
            filesPanel.setCommitFilter(commit)
            heatmapPanel.files = result.commitScoredFiles[commitIdentity(commit)].orEmpty()
            heatmapPanel.overlayPaths = overlayPaths
            commitFilterLabel.text = "Commit: ${commit.hash.take(8)}"
            authorFilterLabel.text = "All authors"
            clearCommitFilterButton.isEnabled = true
            clearAuthorFilterButton.isEnabled = false
            statusLabel.text = "${filesPanel.rowCount} files in commit ${commit.hash.take(8)}"
            updateFilterChips()
            updateSummary(result)
            tabs.selectedIndex = 1
        }

        fun commitCacheFor(repoRootPath: String, branch: String): CommitCache = CommitCache("$repoRootPath#$branch")

        fun metricsCacheFor(repoRootPath: String, branch: String): MetricsCache = MetricsCache("$repoRootPath#$branch")

        val authorPanel = AuthorStatsPanel { author, details ->
            activeAuthorFilter = author
            activeCommitFilterId = null
            val result = currentAnalysisResult?.let(::scopedAnalysisResult)
            val authorCommits = result?.filteredCommits?.filter { it.author == author }.orEmpty()
            val authorCommitEfforts = result?.commitEfforts?.filter { it.author == author }.orEmpty()
            filesPanel.clearCommitFilter()
            commitsPanel.setData(authorCommits, authorCommitEfforts)
            filesPanel.setAuthorFilter(author, details)
            heatmapPanel.files = result?.authorScoredFiles?.get(author).orEmpty()
            heatmapPanel.overlayPaths = overlayPaths
            authorFilterLabel.text = "Author: $author"
            commitFilterLabel.text = "All commits"
            clearAuthorFilterButton.isEnabled = true
            clearCommitFilterButton.isEnabled = false
            statusLabel.text = "${filesPanel.rowCount} files for $author"
            updateFilterChips()
            result?.let { updateSummary(it) }
            tabs.selectedIndex = 1
        }

        fun setBusy(isBusy: Boolean) {
            loadButton.isEnabled = !isBusy
            prButton.isEnabled = !isBusy && !isAggregateSelection()
            clearOverlayButton.isEnabled = !isBusy
            clearAuthorFilterButton.isEnabled = !isBusy && activeAuthorFilter != null
            clearCommitFilterButton.isEnabled = !isBusy && activeCommitFilterId != null
            repositoryCombo.isEnabled = !isBusy && discoveredRoots.isNotEmpty()
            historyBranchCombo.isEnabled = !isBusy && !isAggregateSelection() && knownBranches.isNotEmpty()
            startDateEnabled.isEnabled = !isBusy
            endDateEnabled.isEnabled = !isBusy
            startDateSpinner.setEnabled(!isBusy && startDateEnabled.isSelected)
            endDateSpinner.setEnabled(!isBusy && endDateEnabled.isSelected)
            progressBar.isVisible = isBusy
        }

        fun updateHistoryBranchSelector(branches: List<String>, selectedBranch: String?) {
            updatingHistoryBranchCombo = true
            historyBranchCombo.model = DefaultComboBoxModel(branches.toTypedArray())
            if (selectedBranch != null && branches.contains(selectedBranch)) {
                historyBranchCombo.selectedItem = selectedBranch
            } else if (branches.isNotEmpty()) {
                historyBranchCombo.selectedIndex = 0
            } else {
                historyBranchCombo.selectedItem = null
            }
            historyBranchCombo.isEnabled = branches.isNotEmpty() && !isAggregateSelection()
            updatingHistoryBranchCombo = false
        }

        resetAnalysisState = {
            cachedCommits = emptyList()
            overlayPaths = emptySet()
            overlayScopedCommitIds = emptySet()
            activeAuthorFilter = null
            activeCommitFilterId = null
            currentAnalysisResult = null
            currentLoadedBranch = null
            currentHistoryBranch = null
            knownBranches = emptyList()
            displayedPathToAbsolutePath = emptyMap()
            analysisCache.clear()
            updateHistoryBranchSelector(emptyList(), null)
            configurePathResolver()
            filesPanel.setData(emptyList())
            filesPanel.setOverlayPaths(emptySet())
            filesPanel.clearAuthorFilter()
            filesPanel.clearCommitFilter()
            heatmapPanel.files = emptyList()
            heatmapPanel.overlayPaths = emptySet()
            authorPanel.setData(emptyList(), emptyMap())
            commitsPanel.setData(emptyList(), emptyList())
            commitFilterLabel.text = "All commits"
            authorFilterLabel.text = "All authors"
            overlayLabel.text = "No PR overlay"
            summaryLabel.text = "Commits: 0 | Lines: 0 | Files: 0"
            summaryLabel.toolTipText = null
            updateFilterChips()
        }

        fun currentDateRange(): DateRangeFilter {
            val startDate = if (startDateEnabled.isSelected) {
                spinnerDateToLocalDate(startDateSpinner, zoneId)
            } else {
                null
            }
            val endDate = if (endDateEnabled.isSelected) {
                spinnerDateToLocalDate(endDateSpinner, zoneId)
            } else {
                null
            }
            return DateRangeFilter(startDate = startDate, endDate = endDate)
        }

        fun withDisplayPaths(
            root: GitRootDiscovery.DiscoveredGitRoot,
            commits: List<CommitEvent>
        ): List<CommitEvent> {
            if (!isAggregateSelection()) return commits

            return commits.map { commit ->
                commit.copy(
                    repoId = root.id,
                    repoName = root.name,
                    files = commit.files.map { file ->
                        file.copy(path = displayPathFor(root, file.path))
                    }
                )
            }
        }

        fun updateDisplayedPathMappings(commitsByRoot: Map<GitRootDiscovery.DiscoveredGitRoot, List<CommitEvent>>) {
            displayedPathToAbsolutePath = commitsByRoot.entries
                .flatMap { (root, commits) ->
                    commits.flatMap { commit ->
                        commit.files.map { file ->
                            displayPathFor(root, file.path) to "${root.absolutePath}/${file.path}"
                        }
                    }
                }
                .toMap()
            configurePathResolver()
        }

        fun loadBranchesFor(root: GitRootDiscovery.DiscoveredGitRoot) {
            if (root.id == AGGREGATE_SCOPE_ID) {
                knownBranches = emptyList()
                currentHistoryBranch = null
                updateHistoryBranchSelector(emptyList(), null)
                setBusy(false)
                statusLabel.text = "Selected repository scope: All repositories. Click Load to analyze."
                return
            }
            setBusy(true)
            object : Task.Backgroundable(project, "Loading branches") {
                lateinit var branchChoices: GitBranchProvider.BranchChoices

                override fun run(indicator: ProgressIndicator) {
                    indicator.isIndeterminate = true
                    branchChoices = branchProvider.listBranches(root.absolutePath)
                }

                override fun onSuccess() {
                    setBusy(false)
                    if (currentRepositoryRoot?.id != root.id) return
                    knownBranches = branchChoices.branches
                    currentHistoryBranch = branchChoices.currentBranch ?: branchChoices.branches.firstOrNull()
                    updateHistoryBranchSelector(knownBranches, currentHistoryBranch)
                    statusLabel.text = if (currentHistoryBranch != null) {
                        "Selected repository: ${root.displayName}. Branch: $currentHistoryBranch. Click Load to analyze."
                    } else {
                        "Selected repository: ${root.displayName}. Click Load to analyze."
                    }
                }

                override fun onThrowable(error: Throwable) {
                    setBusy(false)
                    if (currentRepositoryRoot?.id != root.id) return
                    knownBranches = emptyList()
                    currentHistoryBranch = null
                    updateHistoryBranchSelector(emptyList(), null)
                    statusLabel.text = "Branch list load failed for ${root.displayName}"
                    JOptionPane.showMessageDialog(
                        null,
                        error.message ?: "Unable to load repository branches.",
                        "Repository Branches",
                        JOptionPane.ERROR_MESSAGE
                    )
                }
            }.queue()
        }

        fun applyOverlay(changes: List<FileChange>, description: String) {
            overlayPaths = changes.map { it.path }.toSet()
            heatmapPanel.overlayPaths = overlayPaths
            filesPanel.setOverlayPaths(overlayPaths)
            overlayLabel.text = description
            updateFilterChips()
        }

        updateSummary = summary@ { result ->
            val activeCommit = activeCommitFilterId
            val activeAuthor = activeAuthorFilter

            if (activeCommit != null) {
                val commit = result.filteredCommits.firstOrNull { commitIdentity(it) == activeCommit }
                val commitEffort = result.commitEfforts.firstOrNull {
                    "${it.repoId}::${it.commitHash}" == activeCommit
                }?.effort
                if (commit != null && commitEffort != null) {
                    val totalAdded = commit.files.sumOf { it.addedLines }
                    val totalDeleted = commit.files.sumOf { it.deletedLines }
                    val netLines = totalAdded - totalDeleted
                    summaryLabel.text =
                        "${scopePrefix()?.let { "$it | " }.orEmpty()}Commit ${commit.hash.take(8)} | ${formatSignedCompact(netLines)} net lines | ${formatGrouped(commit.files.size)} files | ${formatEffortRangeCompact(commitEffort)}"
                    summaryLabel.toolTipText =
                        "Commit by ${commit.author}, added ${formatGrouped(totalAdded)} lines, deleted ${formatGrouped(totalDeleted)} lines, estimated effort ${formatEffortRangeLong(commitEffort)}"
                    return@summary
                }
            }

            if (activeAuthor != null) {
                val authorMetric = result.authorStats.firstOrNull { it.author == activeAuthor }
                val authorFiles = result.authorFileBreakdown[activeAuthor]
                if (authorMetric != null && authorFiles != null) {
                    val effort = EstimatedEffort(
                        minMinutes = authorMetric.effortMinMinutes,
                        maxMinutes = authorMetric.effortMaxMinutes,
                        score = 0.0,
                        confidence = 0.0,
                        reasons = emptyList()
                    )
                    summaryLabel.text =
                        "${scopePrefix()?.let { "$it | " }.orEmpty()}$activeAuthor | ${formatGrouped(authorMetric.commitCount)} commits | ${formatSignedCompact(authorMetric.netLines)} net lines | ${formatGrouped(authorFiles.size)} files | ${formatEffortRangeCompact(effort)}"
                    summaryLabel.toolTipText =
                        "Author ${authorMetric.author}, added ${formatGrouped(authorMetric.totalAdded)} lines, deleted ${formatGrouped(authorMetric.totalDeleted)} lines, estimated effort ${formatEffortRangeLong(effort)}"
                    return@summary
                }
            }

            summaryLabel.text =
                "${scopePrefix()?.let { "$it | " }.orEmpty()}${formatGrouped(result.commitCount)} commits | ${formatSignedCompact(result.netLines)} net lines | ${formatGrouped(result.scoredFiles.size)} files | ${formatEffortRangeCompact(result.totalEffort)}"
            summaryLabel.toolTipText =
                "Added ${formatGrouped(result.totalAdded)} lines, deleted ${formatGrouped(result.totalDeleted)} lines, net ${formatSignedGrouped(result.netLines)} lines, estimated effort ${formatEffortRangeLong(result.totalEffort)}"
        }

        fun runOverlay(baseBranch: String, targetBranch: String) {
            val repoRootPath = currentRepoPath() ?: return
            overlayGeneration += 1
            val generation = overlayGeneration
            setBusy(true)
            object : Task.Backgroundable(project, "Parsing branch diff") {
                var changes: List<FileChange> = emptyList()
                var prCommits: List<CommitEvent> = emptyList()
                var estimatedEffort: EstimatedEffort = EstimatedEffort.ZERO

                override fun run(indicator: ProgressIndicator) {
                    indicator.isIndeterminate = true
                    changes = diffParser.parseBranchDiff(repoRootPath, baseBranch, targetBranch)
                    prCommits = gitReader.readCommitsInRange(repoRootPath, baseBranch, targetBranch)
                    estimatedEffort = effortHeuristicEngine.estimateDiff(changes)
                }

                override fun onSuccess() {
                    setBusy(false)
                    if (generation != overlayGeneration) return
                    overlayScopedCommitIds = prCommits.map(::commitIdentity).toSet()
                    applyOverlay(
                        changes,
                        "PR: $baseBranch...$targetBranch (${formatGrouped(changes.size)} files, ${formatGrouped(prCommits.size)} commits, ${formatEffortRangeCompact(estimatedEffort)})"
                    )
                    statusLabel.text =
                        "${formatGrouped(changes.size)} files in PR | ${formatGrouped(prCommits.size)} commits | ${formatEffortRangeCompact(estimatedEffort)}"
                    currentAnalysisResult?.let(applyAnalysis)
                }

                override fun onThrowable(error: Throwable) {
                    setBusy(false)
                    if (generation != overlayGeneration) return
                    overlayScopedCommitIds = emptySet()
                    applyOverlay(emptyList(), "No PR overlay")
                    currentAnalysisResult?.let(applyAnalysis)
                    statusLabel.text = "PR overlay failed"
                    JOptionPane.showMessageDialog(
                        null,
                        error.message ?: "Unable to compare branches.",
                        "PR Overlay Error",
                        JOptionPane.ERROR_MESSAGE
                    )
                }
            }.queue()
        }

        applyAnalysis = { result ->
            currentAnalysisResult = result
            val visibleResult = scopedAnalysisResult(result)
            filesPanel.setData(result.scoredFiles)
            filesPanel.setOverlayPaths(overlayPaths)
            authorPanel.setData(visibleResult.authorStats, visibleResult.authorFileBreakdown)
            val activeCommit = activeCommitFilterId
            val activeAuthor = activeAuthorFilter
            if (activeCommit != null) {
                commitsPanel.setShowRepositoryColumn(isAggregateSelection())
                commitsPanel.setData(visibleResult.filteredCommits, visibleResult.commitEfforts)
                val commit = visibleResult.filteredCommits.firstOrNull { commitIdentity(it) == activeCommit }
                if (commit != null) {
                    filesPanel.setCommitFilter(commit)
                    heatmapPanel.files = visibleResult.commitScoredFiles[activeCommit].orEmpty()
                    commitFilterLabel.text = "Commit: ${commit.hash.take(8)}"
                    clearCommitFilterButton.isEnabled = true
                } else {
                    activeCommitFilterId = null
                    filesPanel.clearCommitFilter()
                    heatmapPanel.files = result.scoredFiles
                    commitFilterLabel.text = "All commits"
                    clearCommitFilterButton.isEnabled = false
                }
                authorFilterLabel.text = "All authors"
                clearAuthorFilterButton.isEnabled = false
            } else if (activeAuthor != null) {
                val authorCommits = visibleResult.filteredCommits.filter { it.author == activeAuthor }
                val authorCommitEfforts = visibleResult.commitEfforts.filter { it.author == activeAuthor }
                commitsPanel.setShowRepositoryColumn(isAggregateSelection())
                commitsPanel.setData(authorCommits, authorCommitEfforts)
                val details = visibleResult.authorFileBreakdown[activeAuthor]
                if (details != null) {
                    filesPanel.setAuthorFilter(activeAuthor, details)
                    heatmapPanel.files = visibleResult.authorScoredFiles[activeAuthor].orEmpty()
                    authorFilterLabel.text = "Author: $activeAuthor"
                    clearAuthorFilterButton.isEnabled = true
                } else {
                    activeAuthorFilter = null
                    filesPanel.clearAuthorFilter()
                    heatmapPanel.files = result.scoredFiles
                    authorFilterLabel.text = "All authors"
                    clearAuthorFilterButton.isEnabled = false
                }
                commitFilterLabel.text = "All commits"
                clearCommitFilterButton.isEnabled = false
            } else {
                commitsPanel.setShowRepositoryColumn(isAggregateSelection())
                commitsPanel.setData(visibleResult.filteredCommits, visibleResult.commitEfforts)
                filesPanel.clearCommitFilter()
                filesPanel.clearAuthorFilter()
                heatmapPanel.files = result.scoredFiles
                authorFilterLabel.text = "All authors"
                commitFilterLabel.text = "All commits"
                clearAuthorFilterButton.isEnabled = false
                clearCommitFilterButton.isEnabled = false
            }
            heatmapPanel.overlayPaths = overlayPaths
            statusLabel.text = "${filesPanel.rowCount} files"
            updateFilterChips()
            updateSummary(visibleResult)
        }

        fun currentAnalysisKey(range: DateRangeFilter): AnalysisCacheKey {
            return AnalysisCacheKey(
                repositoryPath = currentScopeKey(),
                branch = currentLoadedBranch ?: currentHistoryBranch,
                headHash = cachedCommits.firstOrNull()?.hash,
                commitCount = cachedCommits.size,
                startDate = range.startDate,
                endDate = range.endDate
            )
        }

        fun computeAnalysis(range: DateRangeFilter): AnalysisResult {
            val filtered = TimeFilter.filter(cachedCommits, range, zoneId)
            val scoredFiles = if (range.isAllTime && !isAggregateSelection()) {
                val repoRootPath = currentRepoPath() ?: return AnalysisResult.EMPTY
                val metricsBranch = currentLoadedBranch ?: currentHistoryBranch ?: DEFAULT_BRANCH_REF
                val lastHash = cachedCommits.firstOrNull()?.hash
                val cachedMetrics = lastHash?.let { metricsCacheFor(repoRootPath, metricsBranch).load(it) }
                val metrics = cachedMetrics ?: metricEngine.aggregate(filtered).also { computed ->
                    if (lastHash != null) {
                        metricsCacheFor(repoRootPath, metricsBranch).save(lastHash, computed)
                    }
                }
                heatScoreCalculator.calculate(metrics)
            } else {
                heatScoreCalculator.calculate(metricEngine.aggregate(filtered))
            }
            val commitEfforts = filtered.map { effortHeuristicEngine.estimateCommit(it) }
            return buildAnalysisResult(filtered, scoredFiles, commitEfforts)
        }

        fun refresh() {
            val selectedRange = currentDateRange()
            if (selectedRange.startDate != null && selectedRange.endDate != null &&
                selectedRange.startDate.isAfter(selectedRange.endDate)
            ) {
                statusLabel.text = "Start date cannot be after end date"
                    JOptionPane.showMessageDialog(
                        null,
                        "Start date cannot be after end date.",
                        "Date Range",
                        JOptionPane.WARNING_MESSAGE
                    )
                    return
                }
            val cacheKey = currentAnalysisKey(selectedRange)
            analysisCache[cacheKey]?.let { cachedResult ->
                applyAnalysis(cachedResult)
                return
            }

            refreshGeneration += 1
            val generation = refreshGeneration
            setBusy(true)

            object : Task.Backgroundable(project, "Calculating metrics") {
                lateinit var result: AnalysisResult

                override fun run(indicator: ProgressIndicator) {
                    indicator.isIndeterminate = true
                    result = computeAnalysis(selectedRange)
                    analysisCache[cacheKey] = result
                }

                override fun onSuccess() {
                    setBusy(false)
                    if (generation != refreshGeneration) return
                    applyAnalysis(result)
                }

                override fun onThrowable(error: Throwable) {
                    setBusy(false)
                    if (generation != refreshGeneration) return
                    statusLabel.text = "Metric calculation failed"
                    JOptionPane.showMessageDialog(
                        null,
                        error.message ?: "Unable to calculate repository metrics.",
                        "Analysis Error",
                        JOptionPane.ERROR_MESSAGE
                    )
                }
            }.queue()
        }

        loadButton = JButton("Load").apply {
            addActionListener {
                val roots = selectedRoots()
                if (roots.isEmpty()) {
                    statusLabel.text = "No repository selected"
                    return@addActionListener
                }
                val historyBranch = currentHistoryBranch ?: DEFAULT_BRANCH_REF
                loadGeneration += 1
                val generation = loadGeneration
                setBusy(true)
                object : Task.Backgroundable(project, "Reading git history") {
                    override fun run(indicator: ProgressIndicator) {
                        indicator.isIndeterminate = true
                        if (isAggregateSelection()) {
                            val commitsByRoot = linkedMapOf<GitRootDiscovery.DiscoveredGitRoot, List<CommitEvent>>()
                            roots.forEach { root ->
                                val aggregateBranch = DEFAULT_BRANCH_REF
                                val cache = commitCacheFor(root.absolutePath, aggregateBranch)
                                val branchCachedCommits = cache.load()
                                val lastHash = branchCachedCommits.firstOrNull()?.hash
                                val loadedCommits = if (lastHash != null && !shouldRefreshFullHistory(branchCachedCommits)) {
                                    val newCommits = try {
                                        gitReader.readCommitsSince(root.absolutePath, lastHash, aggregateBranch)
                                    } catch (error: IllegalStateException) {
                                        if (shouldReloadFromScratch(error)) {
                                            gitReader.readCommits(root.absolutePath, branch = aggregateBranch)
                                        } else {
                                            throw error
                                        }
                                    }

                                    if (newCommits.isNotEmpty()) {
                                        mergeCommits(newCommits, branchCachedCommits)
                                    } else {
                                        branchCachedCommits
                                    }
                                } else {
                                    gitReader.readCommits(root.absolutePath, branch = aggregateBranch)
                                }
                                cache.save(loadedCommits)
                                commitsByRoot[root] = loadedCommits
                            }

                            updateDisplayedPathMappings(commitsByRoot)
                            cachedCommits = commitsByRoot.entries
                                .flatMap { (root, commits) -> withDisplayPaths(root, commits) }
                                .sortedByDescending { it.timestamp }
                            currentLoadedBranch = null
                        } else {
                            val repoRootPath = currentRepoPath() ?: return
                            val cache = commitCacheFor(repoRootPath, historyBranch)
                            val metricsCache = metricsCacheFor(repoRootPath, historyBranch)
                            val branchCachedCommits = cache.load()
                            val lastHash = branchCachedCommits.firstOrNull()?.hash
                            cachedCommits = if (lastHash != null && !shouldRefreshFullHistory(branchCachedCommits)) {
                                val newCommits = try {
                                    gitReader.readCommitsSince(repoRootPath, lastHash, historyBranch)
                                } catch (error: IllegalStateException) {
                                    if (shouldReloadFromScratch(error)) {
                                        gitReader.readCommits(repoRootPath, branch = historyBranch)
                                    } else {
                                        throw error
                                    }
                                }

                                if (newCommits.isNotEmpty()) {
                                    mergeCommits(newCommits, branchCachedCommits)
                                } else {
                                    branchCachedCommits
                                }
                            } else {
                                gitReader.readCommits(repoRootPath, branch = historyBranch)
                            }
                            currentLoadedBranch = historyBranch
                            cache.save(cachedCommits)
                            metricsCache.clear()
                            displayedPathToAbsolutePath = emptyMap()
                            configurePathResolver()
                        }
                        analysisCache.clear()
                    }

                    override fun onSuccess() {
                        setBusy(false)
                        if (generation != loadGeneration) return
                        refresh()
                    }

                    override fun onThrowable(error: Throwable) {
                        setBusy(false)
                        if (generation != loadGeneration) return
                        statusLabel.text = "Git history load failed"
                        summaryLabel.text = "Commits: 0 | Lines: 0 | Files: 0"
                        JOptionPane.showMessageDialog(
                            null,
                            error.message ?: "Unable to read git history.",
                            "Load Error",
                            JOptionPane.ERROR_MESSAGE
                        )
                    }
                }.queue()
            }
        }

        prButton = JButton("PR Overlay").apply {
            toolTipText = "Compare branches inside the selected repository"
            addActionListener {
                val root = currentRepositoryRoot
                if (root == null) {
                    statusLabel.text = "No repository selected"
                    return@addActionListener
                }
                if (isAggregateSelection()) {
                    statusLabel.text = "PR overlay is only available in single-repository mode"
                    return@addActionListener
                }
                setBusy(true)
                object : Task.Backgroundable(project, "Loading branches") {
                    lateinit var branchChoices: GitBranchProvider.BranchChoices

                    override fun run(indicator: ProgressIndicator) {
                        indicator.isIndeterminate = true
                        branchChoices = branchProvider.listBranches(root.absolutePath)
                    }

                    override fun onSuccess() {
                        setBusy(false)
                        if (branchChoices.branches.isEmpty()) {
                            statusLabel.text = "No git branches found"
                            JOptionPane.showMessageDialog(
                                null,
                                "No local or remote branches were found for this repository.",
                                "PR Overlay",
                                JOptionPane.WARNING_MESSAGE
                            )
                            return
                        }

                        knownBranches = branchChoices.branches
                        if (currentHistoryBranch == null) {
                            currentHistoryBranch = branchChoices.currentBranch ?: branchChoices.branches.firstOrNull()
                            updateHistoryBranchSelector(knownBranches, currentHistoryBranch)
                        }
                        val selection = showBranchSelectionDialog(branchChoices) ?: return
                        if (selection.baseBranch.isBlank() || selection.targetBranch.isBlank()) {
                            statusLabel.text = "PR overlay requires both branch names"
                            JOptionPane.showMessageDialog(
                                null,
                                "Base branch and target branch are required.",
                                "PR Overlay",
                                JOptionPane.WARNING_MESSAGE
                            )
                            return
                        }

                        runOverlay(selection.baseBranch, selection.targetBranch)
                    }

                    override fun onThrowable(error: Throwable) {
                        setBusy(false)
                        statusLabel.text = "Branch list load failed"
                        JOptionPane.showMessageDialog(
                            null,
                            error.message ?: "Unable to load repository branches.",
                            "PR Overlay Error",
                            JOptionPane.ERROR_MESSAGE
                        )
                    }
                }.queue()
            }
        }

        clearOverlayButton = JButton("Clear Overlay").apply {
            addActionListener {
                overlayScopedCommitIds = emptySet()
                applyOverlay(emptyList(), "No PR overlay")
                if (currentAnalysisResult != null) {
                    applyAnalysis(currentAnalysisResult ?: AnalysisResult.EMPTY)
                } else {
                    statusLabel.text = "Click 'Load' to analyze repository"
                    updateFilterChips()
                }
            }
        }

        historyBranchCombo.addActionListener {
            if (updatingHistoryBranchCombo) return@addActionListener
            currentHistoryBranch = historyBranchCombo.selectedItem?.toString()?.trim()?.takeUnless { it.isBlank() }
            if (currentHistoryBranch != null && currentHistoryBranch != currentLoadedBranch) {
                statusLabel.text = "Selected branch: $currentHistoryBranch. Click Load to analyze."
            }
        }

        repositoryCombo.addActionListener {
            if (updatingRepositoryCombo) return@addActionListener
            val selectedRoot = repositoryCombo.selectedItem as? GitRootDiscovery.DiscoveredGitRoot ?: return@addActionListener
            if (selectedRoot.id == currentRepositoryRoot?.id) return@addActionListener
            currentRepositoryRoot = selectedRoot
            resetAnalysisState()
            loadBranchesFor(selectedRoot)
            prButton.toolTipText = if (selectedRoot.id == AGGREGATE_SCOPE_ID) {
                "PR overlay is only available in single-repository mode"
            } else {
                "Compare branches inside the selected repository"
            }
        }

        clearAuthorFilterButton = JButton("Clear Author Filter").apply {
            isEnabled = false
            addActionListener {
                activeAuthorFilter = null
                currentAnalysisResult?.let(applyAnalysis)
            }
        }

        clearCommitFilterButton = JButton("Clear Commit Filter").apply {
            isEnabled = false
            addActionListener {
                activeCommitFilterId = null
                currentAnalysisResult?.let(applyAnalysis)
            }
        }

        startDateEnabled.addActionListener {
            startDateSpinner.setEnabled(startDateEnabled.isSelected)
            if (cachedCommits.isNotEmpty()) refresh()
        }
        endDateEnabled.addActionListener {
            endDateSpinner.setEnabled(endDateEnabled.isSelected)
            if (cachedCommits.isNotEmpty()) refresh()
        }
        startDateSpinner.addChangeListener { if (cachedCommits.isNotEmpty() && startDateEnabled.isSelected) refresh() }
        endDateSpinner.addChangeListener { if (cachedCommits.isNotEmpty() && endDateEnabled.isSelected) refresh() }

        val overlayActionGroup = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(JBColor(0xC9CDD3, 0x4B5257)),
                BorderFactory.createEmptyBorder(4, 6, 4, 6)
            )
            add(JLabel("Overlay"))
            add(Box.createHorizontalStrut(8))
            add(prButton)
            add(Box.createHorizontalStrut(6))
            add(clearOverlayButton)
        }

        val actionRow = Box.createHorizontalBox().apply {
            add(overlayActionGroup)
            add(Box.createHorizontalStrut(8))
            add(clearAuthorFilterButton)
            add(Box.createHorizontalStrut(8))
            add(clearCommitFilterButton)
            add(Box.createHorizontalStrut(16))
            add(JLabel("Date range"))
            add(Box.createHorizontalStrut(6))
            add(startDateEnabled)
            add(startDateSpinner)
            add(Box.createHorizontalStrut(8))
            add(endDateEnabled)
            add(endDateSpinner)
            add(Box.createHorizontalGlue())
            add(progressBar)
        }

        val scopeRow = Box.createHorizontalBox().apply {
            add(JLabel("Repository"))
            add(Box.createHorizontalStrut(6))
            add(repositoryCombo)
            add(Box.createHorizontalStrut(16))
            add(JLabel("Branch"))
            add(Box.createHorizontalStrut(6))
            add(historyBranchCombo)
            add(Box.createHorizontalStrut(8))
            add(loadButton)
            add(Box.createHorizontalGlue())
        }

        val infoRow = Box.createHorizontalBox().apply {
            add(JLabel("Summary"))
            add(Box.createHorizontalStrut(6))
            add(summaryLabel)
            add(Box.createHorizontalGlue())
            add(overlayLabel)
            add(Box.createHorizontalStrut(10))
            add(authorFilterLabel)
            add(Box.createHorizontalStrut(10))
            add(commitFilterLabel)
        }

        updateFilterChips()

        val toolbar = Box.createVerticalBox().apply {
            add(scopeRow)
            add(Box.createVerticalStrut(6))
            add(actionRow)
            add(Box.createVerticalStrut(6))
            add(infoRow)
        }

        val interactionListener = HeatmapInteractionListener(
            heatmapPanel,
            project,
            filesPanel::resolveAbsolutePath,
            filesPanel::selectPath
        )
        heatmapPanel.addMouseListener(interactionListener)
        heatmapPanel.addMouseMotionListener(interactionListener)

        tabs = JTabbedPane().apply {
            addTab("Heatmap", JScrollPane(heatmapPanel))
            addTab("Files", JScrollPane(filesPanel))
            addTab("Commits", JScrollPane(commitsPanel))
            addTab("Authors", JScrollPane(authorPanel))
            selectedIndex = 3
        }

        val mainPanel = Box.createVerticalBox().apply {
            add(toolbar)
            add(tabs)
        }

        val content = ContentFactory.getInstance().createContent(mainPanel, "", false)
        toolWindow.contentManager.addContent(content)

        object : Task.Backgroundable(project, "Discovering repositories") {
            lateinit var roots: List<GitRootDiscovery.DiscoveredGitRoot>

            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                roots = rootDiscovery.discoverRoots()
            }

            override fun onSuccess() {
                discoveredRoots = roots
                currentRepositoryRoot = roots.firstOrNull()
                updateRepositorySelector(discoveredRoots, currentRepositoryRoot)
                filesPanel.repositoryRootPath = currentRepoPath()
                resetAnalysisState()
                val selectedRoot = currentRepositoryRoot
                if (selectedRoot != null) {
                    loadBranchesFor(selectedRoot)
                } else {
                    statusLabel.text = "No git repositories found under the project root."
                }
            }
        }.queue()
    }

    private fun mergeCommits(newCommits: List<CommitEvent>, existingCommits: List<CommitEvent>): List<CommitEvent> {
        val byHash = linkedMapOf<String, CommitEvent>()
        (newCommits + existingCommits).forEach { byHash.putIfAbsent(it.hash, it) }
        return byHash.values.toList()
    }

    private fun shouldReloadFromScratch(error: IllegalStateException): Boolean {
        val message = error.message?.lowercase().orEmpty()
        return message.contains("unknown revision") ||
            message.contains("bad revision") ||
            message.contains("ambiguous argument")
    }

    private fun shouldRefreshFullHistory(commits: List<CommitEvent>): Boolean {
        // Earlier versions persisted only the most recent 500 commits.
        return commits.size == LEGACY_COMMIT_LIMIT
    }

    private data class AnalysisCacheKey(
        val repositoryPath: String?,
        val branch: String?,
        val headHash: String?,
        val commitCount: Int,
        val startDate: LocalDate?,
        val endDate: LocalDate?
    )

    private data class AnalysisResult(
        val scoredFiles: List<com.githeatmap.engine.ScoredFile>,
        val authorStats: List<AuthorMetrics>,
        val authorFileBreakdown: Map<String, List<AuthorFileMetrics>>,
        val authorScoredFiles: Map<String, List<com.githeatmap.engine.ScoredFile>>,
        val commitScoredFiles: Map<String, List<com.githeatmap.engine.ScoredFile>>,
        val filteredCommits: List<CommitEvent>,
        val commitEfforts: List<CommitEffortMetrics>,
        val totalEffort: EstimatedEffort,
        val commitCount: Int,
        val totalAdded: Int,
        val totalDeleted: Int
    ) {
        val netLines: Int
            get() = totalAdded - totalDeleted

        companion object {
            val EMPTY = AnalysisResult(
                scoredFiles = emptyList(),
                authorStats = emptyList(),
                authorFileBreakdown = emptyMap(),
                authorScoredFiles = emptyMap(),
                commitScoredFiles = emptyMap(),
                filteredCommits = emptyList(),
                commitEfforts = emptyList(),
                totalEffort = EstimatedEffort.ZERO,
                commitCount = 0,
                totalAdded = 0,
                totalDeleted = 0
            )
        }
    }

    private data class BranchSelection(
        val baseBranch: String,
        val targetBranch: String
    )

    private fun createDateSpinner(): JSpinner {
        return JSpinner(SpinnerDateModel()).apply {
            editor = JSpinner.DateEditor(this, "yyyy-MM-dd")
            setEnabled(false)
        }
    }

    private fun spinnerDateToLocalDate(spinner: JSpinner, zoneId: ZoneId): LocalDate {
        val date = spinner.value as Date
        return Instant.ofEpochMilli(date.time).atZone(zoneId).toLocalDate()
    }

    private fun showBranchSelectionDialog(branchChoices: GitBranchProvider.BranchChoices): BranchSelection? {
        val branches = branchChoices.branches
        val targetDefault = branchChoices.currentBranch ?: branches.first()
        val baseDefault = preferredBaseBranch(branches, targetDefault)

        val baseCombo = createBranchCombo(branches, baseDefault)
        val targetCombo = createBranchCombo(branches, targetDefault)
        val panel = JPanel(GridLayout(0, 1, 0, 8)).apply {
            add(JLabel("Base branch"))
            add(baseCombo)
            add(JLabel("Target branch"))
            add(targetCombo)
            add(JLabel("Type while focused to search; branch selectors are read-only"))
        }

        val result = JOptionPane.showConfirmDialog(
            null,
            panel,
            "PR Overlay",
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE
        )
        if (result != JOptionPane.OK_OPTION) {
            return null
        }

        return BranchSelection(
            baseBranch = baseCombo.selectedItem?.toString()?.trim().orEmpty(),
            targetBranch = targetCombo.selectedItem?.toString()?.trim().orEmpty()
        )
    }

    private fun preferredBaseBranch(branches: List<String>, targetBranch: String): String {
        val candidates = listOf("main", "master", "origin/main", "origin/master")
        return candidates.firstOrNull { it in branches && it != targetBranch }
            ?: branches.firstOrNull { it != targetBranch }
            ?: targetBranch
    }

    private fun createBranchCombo(branches: List<String>, defaultSelection: String): JComboBox<String> {
        return JComboBox(DefaultComboBoxModel(branches.toTypedArray())).apply {
            isEditable = false
            selectedItem = defaultSelection
            prototypeDisplayValue = branches.maxByOrNull { it.length } ?: "origin/main"
            ComboboxSpeedSearch.installOn(this)
        }
    }

    private fun formatGrouped(value: Int): String {
        return NumberFormat.getIntegerInstance(Locale.US).format(value)
    }

    private fun formatSignedGrouped(value: Int): String {
        val prefix = if (value > 0) "+" else ""
        return prefix + formatGrouped(value)
    }

    private fun formatSignedCompact(value: Int): String {
        val absValue = kotlin.math.abs(value.toLong())
        val compact = NumberFormat.getCompactNumberInstance(Locale.US, NumberFormat.Style.SHORT).apply {
            maximumFractionDigits = 1
        }.format(absValue)

        return when {
            value > 0 -> "+$compact"
            value < 0 -> "-$compact"
            else -> "0"
        }
    }

    private fun formatEffortRangeCompact(effort: EstimatedEffort): String {
        return "Effort ${formatMinutesCompact(effort.minMinutes)}-${formatMinutesCompact(effort.maxMinutes)}"
    }

    private fun formatEffortRangeLong(effort: EstimatedEffort): String {
        return "${formatMinutesLong(effort.minMinutes)}-${formatMinutesLong(effort.maxMinutes)}"
    }

    private fun formatMinutesCompact(minutes: Int): String {
        return when {
            minutes >= 8 * 60 -> "%.1fd".format(minutes / 480.0)
            minutes >= 60 -> "%.1fh".format(minutes / 60.0)
            else -> "${minutes}m"
        }
    }

    private fun formatMinutesLong(minutes: Int): String {
        return when {
            minutes >= 8 * 60 -> "%.1f work days".format(minutes / 480.0)
            minutes >= 60 -> "%.1f hours".format(minutes / 60.0)
            else -> "$minutes minutes"
        }
    }

    companion object {
        private const val AGGREGATE_SCOPE_ID = "__all_repositories__"
        private const val LEGACY_COMMIT_LIMIT = 500
        private const val DEFAULT_BRANCH_REF = "HEAD"
    }

    private fun aggregateRepositoryOption(): GitRootDiscovery.DiscoveredGitRoot {
        return GitRootDiscovery.DiscoveredGitRoot(
            id = AGGREGATE_SCOPE_ID,
            name = "All repositories",
            absolutePath = "",
            relativePath = "."
        )
    }
}
