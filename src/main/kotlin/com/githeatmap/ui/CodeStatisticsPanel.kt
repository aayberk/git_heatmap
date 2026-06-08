package com.githeatmap.ui

import com.githeatmap.model.CommitEffortMetrics
import com.githeatmap.model.CommitEvent
import com.intellij.ui.JBColor
import java.awt.BasicStroke
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GridLayout
import java.awt.Insets
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.event.MouseEvent
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.ButtonGroup
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.Scrollable
import javax.swing.JTable
import javax.swing.SwingConstants
import javax.swing.JToggleButton
import javax.swing.ToolTipManager
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer

class CodeStatisticsPanel : JPanel(BorderLayout(8, 8)) {

    private val statusLabel = JLabel("Load repository history to calculate code statistics")
    private val chartPanel = PieChartPanel()
    private val commitsModeButton = createContributionModeButton("Commits")
    private val effortModeButton = createContributionModeButton("Effort")
    private val contributionCalendarPanel = ContributionCalendarPanel()
    private val contributionTrendPanel = ContributionTrendPanel()
    private val hourlyCodingActivityPanel = HourlyCodingActivityPanel()
    private val tableModel = CodeStatisticsTableModel()
    private val table = JTable(tableModel).apply {
        autoCreateRowSorter = true
        fillsViewportHeight = true
        setDefaultRenderer(Int::class.javaObjectType, NumberCellRenderer())
        setDefaultRenderer(Double::class.javaObjectType, PercentCellRenderer())
    }
    private var commits: List<CommitEvent> = emptyList()
    private var commitEfforts: List<CommitEffortMetrics> = emptyList()
    private var contributionMode: ContributionCalendarMode = ContributionCalendarMode.COMMITS

    init {
        border = BorderFactory.createEmptyBorder(8, 8, 8, 8)
        ButtonGroup().apply {
            add(commitsModeButton)
            add(effortModeButton)
        }
        commitsModeButton.addActionListener { setContributionMode(ContributionCalendarMode.COMMITS) }
        effortModeButton.addActionListener { setContributionMode(ContributionCalendarMode.EFFORT) }
        updateContributionModeButtons()

        val toolbar = Box.createHorizontalBox().apply {
            add(statusLabel)
            add(Box.createHorizontalGlue())
        }

        val tableScrollPane = JScrollPane(table).apply {
            preferredSize = Dimension(420, 340)
        }
        val distributionSection = JPanel(BorderLayout(8, 0)).apply {
            preferredSize = Dimension(1288, 340)
            maximumSize = Dimension(Int.MAX_VALUE, 340)
            border = BorderFactory.createEmptyBorder()
            add(chartPanel, BorderLayout.CENTER)
            add(tableScrollPane, BorderLayout.EAST)
        }
        val visualDashboard = ScrollableDashboardPanel().apply {
            add(distributionSection)
            add(Box.createVerticalStrut(10))
            add(contributionSection())
        }
        val center = JScrollPane(visualDashboard).apply {
            border = BorderFactory.createEmptyBorder()
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
            verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
        }

        add(toolbar, BorderLayout.NORTH)
        add(center, BorderLayout.CENTER)
    }

    fun onScopeChanged() {
        tableModel.update(emptyList())
        chartPanel.update(emptyList())
        contributionCalendarPanel.update(emptyList(), emptyList(), contributionMode)
        contributionTrendPanel.update(emptyList(), emptyList(), contributionMode)
        hourlyCodingActivityPanel.update(emptyList(), emptyList(), contributionMode)
        statusLabel.text = "Load repository history to calculate code statistics"
    }

    fun setContributionData(commits: List<CommitEvent>, commitEfforts: List<CommitEffortMetrics>) {
        this.commits = commits
        this.commitEfforts = commitEfforts
        val stats = calculateStats(commits)
        tableModel.update(stats)
        chartPanel.update(stats)
        contributionCalendarPanel.update(commits, commitEfforts, contributionMode)
        contributionTrendPanel.update(commits, commitEfforts, contributionMode)
        hourlyCodingActivityPanel.update(commits, commitEfforts, contributionMode)
        updateStatus(stats, commits.size)
    }

    private fun calculateStats(commits: List<CommitEvent>): List<CodeLanguageStat> {
        val byLanguage = linkedMapOf<String, MutableCodeLanguageStat>()

        commits.forEach { commit ->
            commit.files
                .filter { file -> shouldIncludePath(file.path) }
                .forEach { file ->
                    val language = languageForPath(file.path)
                    val stat = byLanguage.getOrPut(language) { MutableCodeLanguageStat(language) }
                    stat.paths += file.path
                    stat.lineCount += file.addedLines + file.deletedLines
                }
        }

        val totalFiles = byLanguage.values.sumOf { it.paths.size }.coerceAtLeast(1)
        return byLanguage.values
            .map { stat ->
                CodeLanguageStat(
                    language = stat.language,
                    fileCount = stat.paths.size,
                    lineCount = stat.lineCount,
                    percentage = stat.paths.size * 100.0 / totalFiles
                )
            }
            .sortedWith(compareByDescending<CodeLanguageStat> { it.fileCount }.thenBy { it.language })
    }

    private fun shouldIncludePath(path: String): Boolean {
        val fileName = path.substringAfterLast("/")
        if (fileName.startsWith(".")) return false
        return fileName.substringAfterLast(".", missingDelimiterValue = "")
            .lowercase(Locale.US) !in EXCLUDED_EXTENSIONS
    }

    private fun languageForPath(path: String): String {
        val fileName = path.substringAfterLast("/")
        val name = fileName.lowercase(Locale.US)
        val extension = fileName.substringAfterLast(".", missingDelimiterValue = "").lowercase(Locale.US)
        return when {
            name == "dockerfile" -> "Dockerfile"
            name == "makefile" -> "Makefile"
            extension == "kt" || extension == "kts" -> "Kotlin"
            extension == "java" -> "Java"
            extension == "xml" -> "XML"
            extension == "json" -> "JSON"
            extension == "yml" || extension == "yaml" -> "YAML"
            extension == "gradle" -> "Gradle"
            extension == "md" -> "Markdown"
            extension == "properties" -> "Properties"
            extension == "sh" || extension == "bash" || extension == "zsh" -> "Shell"
            extension == "py" -> "Python"
            extension == "cs" -> "C#"
            extension == "js" || extension == "mjs" || extension == "cjs" -> "JavaScript"
            extension == "ts" || extension == "tsx" -> "TypeScript"
            extension == "html" || extension == "htm" -> "HTML"
            extension == "css" || extension == "scss" || extension == "sass" -> "CSS"
            extension.isBlank() -> "No extension"
            else -> extension.uppercase(Locale.US)
        }
    }

    private fun updateStatus(stats: List<CodeLanguageStat>, commitCount: Int) {
        if (commitCount == 0) {
            statusLabel.text = "No commits match current filters"
            return
        }
        val totalFiles = stats.sumOf { it.fileCount }
        val totalLines = stats.sumOf { it.lineCount }
        statusLabel.text =
            "Commits: ${format(commitCount)} | Files: ${format(totalFiles)} | Changed lines: ${format(totalLines)} | Formats: ${stats.size}"
    }

    private fun format(total: Int): String {
        return NumberFormat.getIntegerInstance(Locale.US).format(total)
    }

    private fun contributionSection(): JPanel {
        val header = Box.createHorizontalBox().apply {
            add(JLabel("Contributions calendar"))
            add(Box.createHorizontalStrut(10))
            add(Box.createHorizontalGlue())
            add(contributionModeToggle())
        }

        return JPanel(BorderLayout(0, 6)).apply {
            border = BorderFactory.createEmptyBorder(6, 0, 0, 0)
            preferredSize = Dimension(860, 760)
            maximumSize = Dimension(Int.MAX_VALUE, 760)
            add(header, BorderLayout.NORTH)
            add(
                Box.createVerticalBox().apply {
                    add(contributionCalendarPanel)
                    add(Box.createVerticalStrut(10))
                    add(contributionTrendPanel)
                    add(Box.createVerticalStrut(10))
                    add(hourlyCodingActivityPanel)
                },
                BorderLayout.CENTER
            )
        }
    }

    private fun setContributionMode(mode: ContributionCalendarMode) {
        contributionMode = mode
        updateContributionModeButtons()
        contributionCalendarPanel.update(commits, commitEfforts, contributionMode)
        contributionTrendPanel.update(commits, commitEfforts, contributionMode)
        hourlyCodingActivityPanel.update(commits, commitEfforts, contributionMode)
    }

    private fun contributionModeToggle(): JPanel {
        return JPanel(GridLayout(1, 2, 0, 0)).apply {
            border = BorderFactory.createEmptyBorder(2, 2, 2, 2)
            background = JBColor(0xF8FAFC, 0x1F2937)
            isOpaque = true
            add(commitsModeButton)
            add(effortModeButton)
        }
    }

    private fun createContributionModeButton(text: String): JToggleButton {
        return SegmentedMetricButton(text).apply {
            isFocusPainted = false
            isBorderPainted = false
            isContentAreaFilled = false
            isOpaque = false
            preferredSize = Dimension(92, 28)
            margin = Insets(5, 14, 5, 14)
            font = font.deriveFont(Font.BOLD, 12f)
        }
    }

    private fun updateContributionModeButtons() {
        commitsModeButton.isSelected = contributionMode == ContributionCalendarMode.COMMITS
        effortModeButton.isSelected = contributionMode == ContributionCalendarMode.EFFORT
        styleContributionModeButton(commitsModeButton)
        styleContributionModeButton(effortModeButton)
    }

    private fun styleContributionModeButton(button: JToggleButton) {
        if (button.isSelected) {
            button.foreground = JBColor.WHITE
        } else {
            button.foreground = JBColor(0x475569, 0xCBD5E1)
        }
        button.repaint()
    }

    private data class MutableCodeLanguageStat(
        val language: String,
        val paths: MutableSet<String> = linkedSetOf(),
        var lineCount: Int = 0
    )

    companion object {
        private val EXCLUDED_EXTENSIONS = setOf(
            "class",
            "jar",
            "zip",
            "png",
            "jpg",
            "jpeg",
            "gif",
            "ico",
            "pdf"
        )
    }
}

private data class CodeLanguageStat(
    val language: String,
    val fileCount: Int,
    val lineCount: Int,
    val percentage: Double
)

private class ScrollableDashboardPanel : JPanel(), Scrollable {
    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        background = JBColor.PanelBackground
    }

    override fun getPreferredScrollableViewportSize(): Dimension = preferredSize

    override fun getScrollableUnitIncrement(
        visibleRect: Rectangle,
        orientation: Int,
        direction: Int
    ): Int = 24

    override fun getScrollableBlockIncrement(
        visibleRect: Rectangle,
        orientation: Int,
        direction: Int
    ): Int = (visibleRect.height * 0.85).toInt().coerceAtLeast(120)

    override fun getScrollableTracksViewportWidth(): Boolean = true

    override fun getScrollableTracksViewportHeight(): Boolean = false
}

private class SegmentedMetricButton(text: String) : JToggleButton(text) {
    override fun paintComponent(graphics: Graphics) {
        val g = graphics.create() as Graphics2D
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            if (isSelected) {
                g.color = JBColor(0x2563EB, 0x3B82F6)
                g.fillRoundRect(0, 0, width, height, 18, 18)
                g.color = JBColor(0x1D4ED8, 0x60A5FA)
                g.drawRoundRect(0, 0, width - 1, height - 1, 18, 18)
            } else if (model.isRollover || model.isPressed) {
                g.color = JBColor(0xE2E8F0, 0x334155)
                g.fillRoundRect(0, 0, width, height, 18, 18)
            }
            super.paintComponent(graphics)
        } finally {
            g.dispose()
        }
    }
}

private enum class ContributionCalendarMode(private val label: String) {
    COMMITS("Commits"),
    EFFORT("Effort");

    override fun toString(): String = label
}

private class ContributionCalendarPanel : JPanel() {
    private var mode: ContributionCalendarMode = ContributionCalendarMode.COMMITS
    private var cells: List<ContributionCell> = emptyList()
    private var totalValue = 0
    private var maxValue = 0
    private val numberFormat = NumberFormat.getIntegerInstance(Locale.US)

    init {
        preferredSize = Dimension(860, 240)
        minimumSize = Dimension(760, 220)
        background = JBColor.PanelBackground
        toolTipText = ""
        ToolTipManager.sharedInstance().registerComponent(this)
    }

    fun update(
        commits: List<CommitEvent>,
        commitEfforts: List<CommitEffortMetrics>,
        mode: ContributionCalendarMode
    ) {
        this.mode = mode
        val zoneId = ZoneId.systemDefault()
        val today = LocalDate.now(zoneId)
        val start = today.minusDays(CALENDAR_DAYS - 1L)
        val commitsByDay = commits.groupingBy { commit ->
            ContributionCalendarSupport.epochSecondsToLocalDate(commit.timestamp, zoneId)
        }.eachCount()
        val effortByDay = commitEfforts.groupBy { effort ->
            ContributionCalendarSupport.epochSecondsToLocalDate(effort.timestamp, zoneId)
        }.mapValues { (_, efforts) ->
            efforts.sumOf { effort -> (effort.effort.minMinutes + effort.effort.maxMinutes) / 2 }
        }

        cells = (0 until CALENDAR_DAYS).map { offset ->
            val date = start.plusDays(offset.toLong())
            val commitCount = commitsByDay[date] ?: 0
            val effortMinutes = effortByDay[date] ?: 0
            ContributionCell(
                date = date,
                commitCount = commitCount,
                effortMinutes = effortMinutes,
                value = when (mode) {
                    ContributionCalendarMode.COMMITS -> commitCount
                    ContributionCalendarMode.EFFORT -> effortMinutes
                }
            )
        }
        totalValue = cells.sumOf { it.value }
        maxValue = cells.maxOfOrNull { it.value } ?: 0
        repaint()
    }

    override fun getToolTipText(event: MouseEvent): String? {
        val cell = cellAt(event.x, event.y) ?: return null
        val commitLabel = if (cell.commitCount == 1) "commit" else "commits"
        return "<html>${cell.date}<br>" +
            "${numberFormat.format(cell.commitCount)} $commitLabel<br>" +
            "${formatMinutes(cell.effortMinutes)} estimated effort</html>"
    }

    override fun paintComponent(graphics: Graphics) {
        super.paintComponent(graphics)
        val g = graphics.create() as Graphics2D
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            paintHeader(g)
            if (cells.isEmpty()) {
                paintEmpty(g)
                return
            }
            paintGrid(g)
            paintLegend(g)
        } finally {
            g.dispose()
        }
    }

    private fun paintHeader(g: Graphics2D) {
        g.color = JBColor(0x1F2937, 0xE5E7EB)
        g.font = font.deriveFont(Font.BOLD, 13f)
        val summary = when (mode) {
            ContributionCalendarMode.COMMITS -> "${numberFormat.format(totalValue)} commits in the last 12 months"
            ContributionCalendarMode.EFFORT -> "${formatMinutes(totalValue)} estimated effort in the last 12 months"
        }
        g.drawString(summary, 8, 18)
    }

    private fun paintEmpty(g: Graphics2D) {
        g.color = JBColor.GRAY
        g.font = font.deriveFont(Font.PLAIN, 12f)
        g.drawString("Load repository history to populate the calendar.", 8, 48)
    }

    private fun paintGrid(g: Graphics2D) {
        val geometry = gridGeometry()
        cells.forEachIndexed { index, cell ->
            val week = index / 7
            val day = index % 7
            val x = geometry.startX + week * geometry.columnStep
            val y = geometry.startY + day * geometry.rowStep
            g.color = colorFor(cell.value)
            g.fillRoundRect(x, y, geometry.cellSize, geometry.cellSize, 4, 4)
        }
    }

    private fun cellAt(x: Int, y: Int): ContributionCell? {
        val geometry = gridGeometry()
        val localX = x - geometry.startX
        val localY = y - geometry.startY
        if (localX < 0 || localY < 0) return null

        val week = localX / geometry.columnStep
        val day = localY / geometry.rowStep
        if (day !in 0..6) return null
        if (localX % geometry.columnStep > geometry.cellSize || localY % geometry.rowStep > geometry.cellSize) return null

        return cells.getOrNull(week * 7 + day)
    }

    private fun paintLegend(g: Graphics2D) {
        val x = 8
        val y = (height - 18).coerceAtLeast(142)
        g.color = JBColor(0x6B7280, 0xCBD5E1)
        g.font = font.deriveFont(Font.PLAIN, 11f)
        g.drawString("Less", x, y)
        (0..4).forEach { index ->
            g.color = colorStep(index)
            g.fillRoundRect(x + 34 + index * 16, y - 10, 11, 11, 3, 3)
        }
        g.color = JBColor(0x6B7280, 0xCBD5E1)
        g.drawString("More", x + 120, y)
    }

    private fun gridGeometry(): CalendarGridGeometry {
        val availableWidth = (width - GRID_START_X - GRID_RIGHT_PADDING).coerceAtLeast(720)
        val columnStep = (availableWidth / WEEK_COUNT).coerceAtLeast(MIN_CELL_SIZE + MIN_CELL_GAP)
        val rowStep = ((height - GRID_START_Y - LEGEND_HEIGHT) / 7).coerceAtLeast(MIN_CELL_SIZE + MIN_CELL_GAP)
        val cellSize = minOf(columnStep - MIN_CELL_GAP, rowStep - MIN_CELL_GAP, MAX_CELL_SIZE)
            .coerceAtLeast(MIN_CELL_SIZE)
        return CalendarGridGeometry(
            startX = GRID_START_X,
            startY = GRID_START_Y,
            columnStep = columnStep,
            rowStep = rowStep,
            cellSize = cellSize
        )
    }

    private fun colorFor(value: Int): Color {
        if (value <= 0 || maxValue <= 0) return colorStep(0)
        val ratio = value.toDouble() / maxValue
        val step = when {
            ratio >= 0.8 -> 4
            ratio >= 0.55 -> 3
            ratio >= 0.3 -> 2
            else -> 1
        }
        return colorStep(step)
    }

    private fun colorStep(step: Int): Color {
        return when (step) {
            0 -> JBColor(0xEBEDF0, 0x2D333B)
            1 -> JBColor(0x9BE9A8, 0x0E4429)
            2 -> JBColor(0x40C463, 0x006D32)
            3 -> JBColor(0x30A14E, 0x26A641)
            else -> JBColor(0x216E39, 0x39D353)
        }
    }

    private fun formatMinutes(minutes: Int): String {
        return when {
            minutes >= 8 * 60 -> "%.1f days".format(minutes / 480.0)
            minutes >= 60 -> "%.1f hours".format(minutes / 60.0)
            else -> "$minutes minutes"
        }
    }

    private data class ContributionCell(
        val date: LocalDate,
        val commitCount: Int,
        val effortMinutes: Int,
        val value: Int
    )

    private data class CalendarGridGeometry(
        val startX: Int,
        val startY: Int,
        val columnStep: Int,
        val rowStep: Int,
        val cellSize: Int
    )

    companion object {
        private const val CALENDAR_DAYS = 371
        private const val WEEK_COUNT = 53
        private const val GRID_START_X = 8
        private const val GRID_START_Y = 34
        private const val GRID_RIGHT_PADDING = 16
        private const val LEGEND_HEIGHT = 48
        private const val MIN_CELL_SIZE = 11
        private const val MAX_CELL_SIZE = 20
        private const val MIN_CELL_GAP = 3
    }
}

private class ContributionTrendPanel : JPanel() {
    private var mode: ContributionCalendarMode = ContributionCalendarMode.COMMITS
    private var points: List<ContributionTrendPoint> = emptyList()
    private var maxValue = 0
    private val numberFormat = NumberFormat.getIntegerInstance(Locale.US)

    init {
        preferredSize = Dimension(860, 240)
        minimumSize = Dimension(760, 220)
        background = JBColor.PanelBackground
        toolTipText = ""
        ToolTipManager.sharedInstance().registerComponent(this)
    }

    fun update(
        commits: List<CommitEvent>,
        commitEfforts: List<CommitEffortMetrics>,
        mode: ContributionCalendarMode
    ) {
        this.mode = mode
        val zoneId = ZoneId.systemDefault()
        val today = LocalDate.now(zoneId)
        val start = today.minusDays(TREND_DAYS - 1L)
        val commitsByDay = commits.groupingBy { commit ->
            ContributionCalendarSupport.epochSecondsToLocalDate(commit.timestamp, zoneId)
        }.eachCount()
        val effortByDay = commitEfforts.groupBy { effort ->
            ContributionCalendarSupport.epochSecondsToLocalDate(effort.timestamp, zoneId)
        }.mapValues { (_, efforts) ->
            efforts.sumOf { effort -> (effort.effort.minMinutes + effort.effort.maxMinutes) / 2 }
        }

        points = (0 until TREND_DAYS).map { offset ->
            val date = start.plusDays(offset.toLong())
            val commitCount = commitsByDay[date] ?: 0
            val effortMinutes = effortByDay[date] ?: 0
            ContributionTrendPoint(
                date = date,
                commitCount = commitCount,
                effortMinutes = effortMinutes,
                value = when (mode) {
                    ContributionCalendarMode.COMMITS -> commitCount
                    ContributionCalendarMode.EFFORT -> effortMinutes
                }
            )
        }
        maxValue = points.maxOfOrNull { it.value } ?: 0
        repaint()
    }

    override fun getToolTipText(event: MouseEvent): String? {
        val point = pointAt(event.x) ?: return null
        val commitLabel = if (point.commitCount == 1) "commit" else "commits"
        return "<html>${point.date}<br>" +
            "${numberFormat.format(point.commitCount)} $commitLabel<br>" +
            "${formatMinutes(point.effortMinutes)} estimated effort</html>"
    }

    override fun paintComponent(graphics: Graphics) {
        super.paintComponent(graphics)
        val g = graphics.create() as Graphics2D
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            paintTitle(g)
            paintGrid(g)
            if (points.isEmpty() || maxValue <= 0) {
                paintEmpty(g)
                return
            }
            paintLine(g)
        } finally {
            g.dispose()
        }
    }

    private fun paintTitle(g: Graphics2D) {
        val total = points.sumOf { it.value }
        val summary = when (mode) {
            ContributionCalendarMode.COMMITS -> "${numberFormat.format(total)} commits"
            ContributionCalendarMode.EFFORT -> formatMinutes(total)
        }
        g.color = JBColor(0x1F2937, 0xE5E7EB)
        g.font = font.deriveFont(Font.BOLD, 13f)
        g.drawString("Last 30 days trend", PLOT_X, 18)
        g.color = JBColor(0x6B7280, 0xCBD5E1)
        g.font = font.deriveFont(Font.PLAIN, 12f)
        g.drawString(summary, PLOT_X + 132, 18)
    }

    private fun paintGrid(g: Graphics2D) {
        val plotWidth = plotWidth()
        g.color = JBColor(0xE5E7EB, 0x374151)
        g.stroke = BasicStroke(1f)
        (0..3).forEach { index ->
            val y = PLOT_Y + index * PLOT_HEIGHT / 3
            g.drawLine(PLOT_X, y, PLOT_X + plotWidth, y)
        }
        g.color = JBColor(0xCBD5E1, 0x4B5563)
        g.drawLine(PLOT_X, PLOT_Y, PLOT_X, PLOT_Y + PLOT_HEIGHT)
        g.drawLine(PLOT_X, PLOT_Y + PLOT_HEIGHT, PLOT_X + plotWidth, PLOT_Y + PLOT_HEIGHT)

        g.color = JBColor(0x6B7280, 0xCBD5E1)
        g.font = font.deriveFont(Font.PLAIN, 11f)
        g.drawString(formatAxisValue(maxValue), 8, PLOT_Y + 4)
        g.drawString("0", 28, PLOT_Y + PLOT_HEIGHT + 4)
        points.firstOrNull()?.let { g.drawString(it.date.month.name.take(3), PLOT_X, PLOT_Y + PLOT_HEIGHT + 22) }
        points.lastOrNull()?.let { g.drawString(it.date.month.name.take(3), PLOT_X + plotWidth - 24, PLOT_Y + PLOT_HEIGHT + 22) }
    }

    private fun paintEmpty(g: Graphics2D) {
        g.color = JBColor.GRAY
        g.font = font.deriveFont(Font.PLAIN, 12f)
        g.drawString("No commit or effort activity in the last 30 days.", PLOT_X, PLOT_Y + 42)
    }

    private fun paintLine(g: Graphics2D) {
        val coordinates = points.mapIndexed { index, point ->
            val x = xForIndex(index)
            val ratio = point.value.toDouble() / maxValue.coerceAtLeast(1)
            val y = PLOT_Y + PLOT_HEIGHT - (ratio * PLOT_HEIGHT).toInt()
            x to y
        }

        g.color = JBColor(0x2563EB, 0x60A5FA)
        g.stroke = BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        coordinates.zipWithNext().forEach { (from, to) ->
            g.drawLine(from.first, from.second, to.first, to.second)
        }

        coordinates.forEachIndexed { index, point ->
            if (points[index].value > 0) {
                g.color = JBColor(0xEFF6FF, 0x1E3A8A)
                g.fillOval(point.first - 4, point.second - 4, 8, 8)
                g.color = JBColor(0x2563EB, 0x93C5FD)
                g.drawOval(point.first - 4, point.second - 4, 8, 8)
            }
        }
    }

    private fun pointAt(x: Int): ContributionTrendPoint? {
        if (points.isEmpty()) return null
        val plotWidth = plotWidth()
        if (x < PLOT_X || x > PLOT_X + plotWidth) return null
        val interval = plotWidth.toDouble() / (TREND_DAYS - 1)
        val index = ((x - PLOT_X) / interval).toInt().coerceIn(0, points.lastIndex)
        return points.getOrNull(index)
    }

    private fun xForIndex(index: Int): Int {
        return PLOT_X + (index * plotWidth().toDouble() / (TREND_DAYS - 1)).toInt()
    }

    private fun plotWidth(): Int {
        return (width - PLOT_X - PLOT_RIGHT_PADDING).coerceAtLeast(420)
    }

    private fun formatAxisValue(value: Int): String {
        return when (mode) {
            ContributionCalendarMode.COMMITS -> numberFormat.format(value)
            ContributionCalendarMode.EFFORT -> formatMinutes(value)
        }
    }

    private fun formatMinutes(minutes: Int): String {
        return when {
            minutes >= 8 * 60 -> "%.1f days".format(minutes / 480.0)
            minutes >= 60 -> "%.1f hours".format(minutes / 60.0)
            else -> "$minutes minutes"
        }
    }

    private data class ContributionTrendPoint(
        val date: LocalDate,
        val commitCount: Int,
        val effortMinutes: Int,
        val value: Int
    )

    companion object {
        private const val TREND_DAYS = 30
        private const val PLOT_X = 46
        private const val PLOT_Y = 36
        private const val PLOT_HEIGHT = 150
        private const val PLOT_RIGHT_PADDING = 24
    }
}

private class HourlyCodingActivityPanel : JPanel() {
    private var mode: ContributionCalendarMode = ContributionCalendarMode.COMMITS
    private var buckets: List<HourlyCodingBucket> = emptyList()
    private var maxValue = 0.0
    private val numberFormat = NumberFormat.getIntegerInstance(Locale.US)

    init {
        preferredSize = Dimension(860, 240)
        minimumSize = Dimension(760, 220)
        background = JBColor.PanelBackground
        toolTipText = ""
        ToolTipManager.sharedInstance().registerComponent(this)
    }

    fun update(
        commits: List<CommitEvent>,
        commitEfforts: List<CommitEffortMetrics>,
        mode: ContributionCalendarMode
    ) {
        this.mode = mode
        val zoneId = ZoneId.systemDefault()
        val today = LocalDate.now(zoneId)
        val start = today.minusDays(DAYS - 1L)
        val commitCounts = IntArray(HOURS)
        val effortMinutes = IntArray(HOURS)

        commits.forEach { commit ->
            val dateTime = Instant.ofEpochSecond(commit.timestamp).atZone(zoneId)
            if (dateTime.toLocalDate() in start..today) {
                commitCounts[dateTime.hour] += 1
            }
        }
        commitEfforts.forEach { effort ->
            val dateTime = Instant.ofEpochSecond(effort.timestamp).atZone(zoneId)
            if (dateTime.toLocalDate() in start..today) {
                effortMinutes[dateTime.hour] += (effort.effort.minMinutes + effort.effort.maxMinutes) / 2
            }
        }

        buckets = (0 until HOURS).map { hour ->
            HourlyCodingBucket(
                hour = hour,
                commitCount = commitCounts[hour],
                effortMinutes = effortMinutes[hour],
                averageCommitsPerDay = commitCounts[hour].toDouble() / DAYS,
                averageEffortMinutesPerDay = effortMinutes[hour].toDouble() / DAYS
            )
        }
        maxValue = buckets.maxOfOrNull { it.selectedValue(mode) } ?: 0.0
        repaint()
    }

    override fun getToolTipText(event: MouseEvent): String? {
        val bucket = bucketAt(event.x) ?: return null
        return "<html>${hourLabel(bucket.hour)}<br>" +
            "${numberFormat.format(bucket.commitCount)} commits in 30 days<br>" +
            "%.2f avg commits/day<br>".format(bucket.averageCommitsPerDay) +
            "${formatMinutes(bucket.averageEffortMinutesPerDay)}/day avg effort<br>" +
            "${formatMinutes(bucket.effortMinutes.toDouble())} total effort</html>"
    }

    override fun paintComponent(graphics: Graphics) {
        super.paintComponent(graphics)
        val g = graphics.create() as Graphics2D
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            paintTitle(g)
            paintGrid(g)
            if (buckets.isEmpty() || maxValue <= 0.0) {
                paintEmpty(g)
                return
            }
            paintBars(g)
        } finally {
            g.dispose()
        }
    }

    private fun paintTitle(g: Graphics2D) {
        val peak = buckets.maxByOrNull { it.selectedValue(mode) }
        val summary = peak
            ?.takeIf { it.selectedValue(mode) > 0.0 }
            ?.let { "Peak ${hourLabel(it.hour)} | ${formatAxisValue(it.selectedValue(mode))}/day" }
            ?: "No hourly activity"
        val title = when (mode) {
            ContributionCalendarMode.COMMITS -> "Average hourly commits"
            ContributionCalendarMode.EFFORT -> "Average hourly coding duration"
        }

        g.color = JBColor(0x1F2937, 0xE5E7EB)
        g.font = font.deriveFont(Font.BOLD, 13f)
        g.drawString(title, PLOT_X, 18)
        g.color = JBColor(0x6B7280, 0xCBD5E1)
        g.font = font.deriveFont(Font.PLAIN, 12f)
        g.drawString(summary, PLOT_X + 190, 18)
    }

    private fun paintGrid(g: Graphics2D) {
        val plotWidth = plotWidth()
        g.color = JBColor(0xE5E7EB, 0x374151)
        g.stroke = BasicStroke(1f)
        (0..3).forEach { index ->
            val y = PLOT_Y + index * PLOT_HEIGHT / 3
            g.drawLine(PLOT_X, y, PLOT_X + plotWidth, y)
        }
        g.color = JBColor(0xCBD5E1, 0x4B5563)
        g.drawLine(PLOT_X, PLOT_Y, PLOT_X, PLOT_Y + PLOT_HEIGHT)
        g.drawLine(PLOT_X, PLOT_Y + PLOT_HEIGHT, PLOT_X + plotWidth, PLOT_Y + PLOT_HEIGHT)

        g.color = JBColor(0x6B7280, 0xCBD5E1)
        g.font = font.deriveFont(Font.PLAIN, 11f)
        g.drawString(formatAxisValue(maxValue), 4, PLOT_Y + 4)
        g.drawString("0", 28, PLOT_Y + PLOT_HEIGHT + 4)
        listOf(0, 6, 12, 18, 23).forEach { hour ->
            val x = xForHour(hour)
            g.drawString(hour.toString().padStart(2, '0'), x - 6, PLOT_Y + PLOT_HEIGHT + 22)
        }
    }

    private fun paintEmpty(g: Graphics2D) {
        g.color = JBColor.GRAY
        g.font = font.deriveFont(Font.PLAIN, 12f)
        val message = when (mode) {
            ContributionCalendarMode.COMMITS -> "No commit activity in the last 30 days."
            ContributionCalendarMode.EFFORT -> "No estimated effort activity in the last 30 days."
        }
        g.drawString(message, PLOT_X, PLOT_Y + 42)
    }

    private fun paintBars(g: Graphics2D) {
        val barStep = plotWidth().toDouble() / HOURS
        val barWidth = (barStep * 0.66).toInt().coerceAtLeast(8)
        buckets.forEach { bucket ->
            val value = bucket.selectedValue(mode)
            val ratio = value / maxValue.coerceAtLeast(1.0)
            val barHeight = (ratio * PLOT_HEIGHT).toInt().coerceAtLeast(if (value > 0.0) 3 else 0)
            val x = xForHour(bucket.hour) - barWidth / 2
            val y = PLOT_Y + PLOT_HEIGHT - barHeight
            g.color = barColor(bucket.hour)
            g.fillRoundRect(x, y, barWidth, barHeight, 6, 6)
        }
    }

    private fun bucketAt(x: Int): HourlyCodingBucket? {
        if (buckets.isEmpty()) return null
        val plotWidth = plotWidth()
        if (x < PLOT_X || x > PLOT_X + plotWidth) return null
        val hour = (((x - PLOT_X).toDouble() / plotWidth) * HOURS).toInt().coerceIn(0, HOURS - 1)
        return buckets.getOrNull(hour)
    }

    private fun xForHour(hour: Int): Int {
        val barStep = plotWidth().toDouble() / HOURS
        return PLOT_X + (hour * barStep + barStep / 2).toInt()
    }

    private fun plotWidth(): Int {
        return (width - PLOT_X - PLOT_RIGHT_PADDING).coerceAtLeast(420)
    }

    private fun formatAxisValue(value: Double): String {
        return when (mode) {
            ContributionCalendarMode.COMMITS -> "%.2f".format(value)
            ContributionCalendarMode.EFFORT -> formatMinutes(value)
        }
    }

    private fun barColor(hour: Int): Color {
        return when (hour) {
            in 6..11 -> JBColor(0x0EA5E9, 0x38BDF8)
            in 12..17 -> JBColor(0x2563EB, 0x60A5FA)
            in 18..23 -> JBColor(0x7C3AED, 0xA78BFA)
            else -> JBColor(0x64748B, 0x94A3B8)
        }
    }

    private fun hourLabel(hour: Int): String {
        return "${hour.toString().padStart(2, '0')}:00-${((hour + 1) % HOURS).toString().padStart(2, '0')}:00"
    }

    private fun formatMinutes(minutes: Double): String {
        return when {
            minutes >= 8 * 60 -> "%.1f days".format(minutes / 480.0)
            minutes >= 60 -> "%.1f hours".format(minutes / 60.0)
            else -> "%.1f min".format(minutes)
        }
    }

    private data class HourlyCodingBucket(
        val hour: Int,
        val commitCount: Int,
        val effortMinutes: Int,
        val averageCommitsPerDay: Double,
        val averageEffortMinutesPerDay: Double
    ) {
        fun selectedValue(mode: ContributionCalendarMode): Double {
            return when (mode) {
                ContributionCalendarMode.COMMITS -> averageCommitsPerDay
                ContributionCalendarMode.EFFORT -> averageEffortMinutesPerDay
            }
        }
    }

    companion object {
        private const val DAYS = 30
        private const val HOURS = 24
        private const val PLOT_X = 46
        private const val PLOT_Y = 36
        private const val PLOT_HEIGHT = 150
        private const val PLOT_RIGHT_PADDING = 24
    }
}

private class CodeStatisticsTableModel : AbstractTableModel() {
    private var stats: List<CodeLanguageStat> = emptyList()
    private val numberFormat = NumberFormat.getIntegerInstance(Locale.US)

    fun update(stats: List<CodeLanguageStat>) {
        this.stats = stats
        fireTableDataChanged()
    }

    override fun getRowCount(): Int = stats.size

    override fun getColumnCount(): Int = 4

    override fun getColumnName(column: Int): String {
        return when (column) {
            0 -> "Language / Format"
            1 -> "Files"
            2 -> "Changed Lines"
            3 -> "%"
            else -> ""
        }
    }

    override fun getColumnClass(columnIndex: Int): Class<*> {
        return when (columnIndex) {
            1, 2 -> Int::class.javaObjectType
            3 -> Double::class.javaObjectType
            else -> String::class.java
        }
    }

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        val stat = stats[rowIndex]
        return when (columnIndex) {
            0 -> stat.language
            1 -> stat.fileCount
            2 -> stat.lineCount
            3 -> stat.percentage
            else -> ""
        }
    }

    override fun setValueAt(aValue: Any?, rowIndex: Int, columnIndex: Int) = Unit

    fun formatNumber(value: Int): String = numberFormat.format(value)
}

private class NumberCellRenderer : DefaultTableCellRenderer() {
    private val numberFormat = NumberFormat.getIntegerInstance(Locale.US)

    init {
        horizontalAlignment = SwingConstants.RIGHT
    }

    override fun setValue(value: Any?) {
        text = if (value is Number) numberFormat.format(value) else value?.toString().orEmpty()
    }
}

private class PercentCellRenderer : DefaultTableCellRenderer() {
    init {
        horizontalAlignment = SwingConstants.RIGHT
    }

    override fun setValue(value: Any?) {
        text = if (value is Number) "%.1f%%".format(value.toDouble()) else value?.toString().orEmpty()
    }
}

private class PieChartPanel : JPanel() {
    private var stats: List<CodeLanguageStat> = emptyList()
    private val palette = listOf(
        JBColor(0x2563EB, 0x60A5FA),
        JBColor(0x16A34A, 0x4ADE80),
        JBColor(0xEA580C, 0xFB923C),
        JBColor(0x9333EA, 0xC084FC),
        JBColor(0x0891B2, 0x22D3EE),
        JBColor(0xDC2626, 0xF87171),
        JBColor(0x4F46E5, 0x818CF8),
        JBColor(0xCA8A04, 0xFACC15),
        JBColor(0x0F766E, 0x2DD4BF),
        JBColor(0xBE185D, 0xF472B6)
    )

    init {
        preferredSize = Dimension(860, 340)
        minimumSize = Dimension(620, 300)
        background = JBColor.PanelBackground
    }

    fun update(stats: List<CodeLanguageStat>) {
        this.stats = stats
        repaint()
    }

    override fun paintComponent(graphics: Graphics) {
        super.paintComponent(graphics)
        val g = graphics.create() as Graphics2D
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            if (stats.isEmpty()) {
                paintEmpty(g)
                return
            }

            val total = stats.sumOf { it.fileCount }.coerceAtLeast(1)
            val size = minOf(width.coerceAtMost(420), height) - 80
            if (size <= 80) {
                paintEmpty(g)
                return
            }
            val x = 40
            val y = (height - size) / 2
            var startAngle = 90.0

            stats.take(MAX_SLICES).forEachIndexed { index, stat ->
                val angle = stat.fileCount * 360.0 / total
                g.color = palette[index % palette.size]
                g.fillArc(x, y, size, size, startAngle.toInt(), (-angle).toInt())
                startAngle -= angle
            }

            g.color = JBColor(0x1F2937, 0xE5E7EB)
            g.font = font.deriveFont(Font.BOLD, 14f)
            g.drawString("File format distribution", x, 24)

            paintLegend(g, x + size + 32, y)
        } finally {
            g.dispose()
        }
    }

    private fun paintEmpty(g: Graphics2D) {
        g.color = JBColor.GRAY
        g.font = font.deriveFont(Font.PLAIN, 14f)
        g.drawString("No code statistics calculated yet.", 32, 42)
    }

    private fun paintLegend(g: Graphics2D, x: Int, y: Int) {
        var currentY = y + 10
        stats.take(MAX_SLICES).forEachIndexed { index, stat ->
            g.color = palette[index % palette.size]
            g.fillRoundRect(x, currentY - 10, 14, 14, 4, 4)
            g.color = JBColor(0x111827, 0xF8FAFC)
            g.font = font.deriveFont(Font.PLAIN, 12f)
            g.drawString("${stat.language} (${stat.fileCount})", x + 22, currentY + 2)
            currentY += 22
        }
    }

    companion object {
        private const val MAX_SLICES = 10
    }
}
