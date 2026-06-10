package com.githeatmap.ui

import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.event.MouseEvent
import java.io.File
import java.text.NumberFormat
import java.util.Locale
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.ButtonGroup
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTable
import javax.swing.JToggleButton
import javax.swing.SwingConstants
import javax.swing.ToolTipManager
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer

class TokenUsagePanel(private val project: Project) : JPanel(BorderLayout(8, 8)) {
    private val statusLabel = JLabel("No token usage data loaded")
    private val reloadButton = JButton("Reload")
    private val claudeModel = TokenUsageTableModel(showReasoning = false)
    private val codexModel = TokenUsageTableModel(showReasoning = true)
    private val trendPanel = TokenUsageTrendPanel()

    init {
        border = BorderFactory.createEmptyBorder(8, 8, 8, 8)
        reloadButton.addActionListener { reload() }

        val toolbar = Box.createHorizontalBox().apply {
            add(JLabel("Token Usage"))
            add(Box.createHorizontalStrut(12))
            add(statusLabel)
            add(Box.createHorizontalGlue())
            add(reloadButton)
        }

        val tables = Box.createVerticalBox().apply {
            add(trendPanel)
            add(Box.createVerticalStrut(12))
            add(providerSection("Claude", claudeModel))
            add(Box.createVerticalStrut(12))
            add(providerSection("Codex", codexModel))
        }

        add(toolbar, BorderLayout.NORTH)
        add(JScrollPane(tables).apply {
            preferredSize = Dimension(980, 620)
            tuneTokenUsageScrolling()
        }, BorderLayout.CENTER)
        reload()
    }

    fun reload() {
        val claude = TokenUsageSupport.readClaudeUsage(claudeRoots())
        val codex = TokenUsageSupport.readCodexUsage(codexRoots())
        claudeModel.update(claude.rows)
        codexModel.update(codex.rows)
        trendPanel.update(claude.rows, codex.rows)
        val rows = claude.rows + codex.rows
        val files = claude.filesScanned + codex.filesScanned
        val total = rows.filter { it.isTotal }.sumOf { it.totalTokens }
        val price = rows.filter { it.isTotal }.sumOf { it.priceUsd }
        val fallbackCount = rows.filter { it.isTotal }.sumOf { it.fallbackPriceCount }
        val fallbackNote = if (fallbackCount > 0) " | Latest known pricing used for ${format(fallbackCount)} records" else ""
        statusLabel.text = "Scanned ${format(files)} local usage files | Total ${format(total)} tokens | Estimated ${formatUsd(price)}$fallbackNote"
    }

    private fun providerSection(title: String, model: TokenUsageTableModel): JPanel {
        val table = JTable(model).apply {
            fillsViewportHeight = true
            autoCreateRowSorter = true
            setDefaultRenderer(Long::class.javaObjectType, TokenUsageNumberRenderer(model))
            setDefaultRenderer(Double::class.javaObjectType, TokenUsagePriceRenderer(model))
            setDefaultRenderer(String::class.java, TokenUsageTextRenderer(model))
            rowHeight = 24
        }
        return JPanel(BorderLayout(0, 6)).apply {
            border = BorderFactory.createEmptyBorder(0, 0, 0, 0)
            maximumSize = Dimension(Int.MAX_VALUE, 220)
            preferredSize = Dimension(940, 220)
            add(JLabel(title).apply { font = font.deriveFont(java.awt.Font.BOLD, 13f) }, BorderLayout.NORTH)
            add(JScrollPane(table).apply { tuneTokenUsageScrolling() }, BorderLayout.CENTER)
        }
    }

    private fun claudeRoots(): List<File> {
        val configured = envPaths("CLAUDE_CONFIG_DIR")
        return configured.ifEmpty {
            listOf(
                homeDir().resolve(".config/claude"),
                homeDir().resolve(".claude")
            )
        }.filter { it.isDirectory }
    }

    private fun codexRoots(): List<File> {
        return envPaths("CODEX_HOME")
            .ifEmpty { listOf(homeDir().resolve(".codex")) }
            .filter { it.isDirectory }
    }

    private fun envPaths(name: String): List<File> {
        return System.getenv(name)
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.map { path -> File(path.replaceFirst("~", homeDir().absolutePath)) }
            .orEmpty()
    }

    private fun homeDir(): File = File(System.getProperty("user.home"))

    private fun format(value: Long): String = NumberFormat.getIntegerInstance(Locale.US).format(value)

    private fun format(value: Int): String = NumberFormat.getIntegerInstance(Locale.US).format(value)

    private fun formatUsd(value: Double): String = NumberFormat.getCurrencyInstance(Locale.US).format(value)

    private fun JScrollPane.tuneTokenUsageScrolling() {
        verticalScrollBar.unitIncrement = 28
        verticalScrollBar.blockIncrement = 220
        horizontalScrollBar.unitIncrement = 28
        horizontalScrollBar.blockIncrement = 220
    }
}

private class TokenUsageTableModel(private val showReasoning: Boolean) : AbstractTableModel() {
    private var rows: List<TokenUsageRow> = emptyList()
    private val columns = buildList {
        add(TokenUsageColumn.Month)
        add(TokenUsageColumn.Input)
        add(TokenUsageColumn.CacheWrite)
        add(TokenUsageColumn.CacheRead)
        add(TokenUsageColumn.Output)
        if (showReasoning) add(TokenUsageColumn.Reasoning)
        add(TokenUsageColumn.Total)
        add(TokenUsageColumn.Price)
    }

    fun update(rows: List<TokenUsageRow>) {
        this.rows = rows
        fireTableDataChanged()
    }

    override fun getRowCount(): Int = rows.size

    override fun getColumnCount(): Int = columns.size

    override fun getColumnName(column: Int): String {
        return columns.getOrNull(column)?.title.orEmpty()
    }

    override fun getColumnClass(columnIndex: Int): Class<*> {
        return when (columns.getOrNull(columnIndex)) {
            TokenUsageColumn.Month -> String::class.java
            TokenUsageColumn.Price -> Double::class.javaObjectType
            else -> Long::class.javaObjectType
        }
    }

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        val row = rows[rowIndex]
        return when (columns.getOrNull(columnIndex)) {
            TokenUsageColumn.Month -> row.month
            TokenUsageColumn.Input -> row.inputTokens
            TokenUsageColumn.CacheWrite -> row.cacheCreationTokens
            TokenUsageColumn.CacheRead -> row.cacheReadTokens
            TokenUsageColumn.Output -> row.outputTokens
            TokenUsageColumn.Reasoning -> row.reasoningTokens
            TokenUsageColumn.Total -> row.totalTokens
            TokenUsageColumn.Price -> row.priceUsd
            null -> ""
        }
    }

    fun isTotalRow(rowIndex: Int): Boolean = rows.getOrNull(rowIndex)?.isTotal == true

    fun fallbackPriceCount(rowIndex: Int): Int = rows.getOrNull(rowIndex)?.fallbackPriceCount ?: 0
}

private enum class TokenUsageColumn(val title: String) {
    Month("Month"),
    Input("Input"),
    CacheWrite("Cache Write"),
    CacheRead("Cache Read"),
    Output("Output"),
    Reasoning("Reasoning"),
    Total("Total"),
    Price("Price")
}

private class TokenUsageTrendPanel : JPanel(BorderLayout(0, 8)) {
    private val chart = TokenUsageChart()
    private val claudeToggle = JCheckBox("Claude", true)
    private val codexToggle = JCheckBox("Codex", true)
    private val tokensToggle = JToggleButton("Tokens", true)
    private val priceToggle = JToggleButton("Price")
    private val titleLabel = JLabel("Monthly total tokens")

    init {
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(JBColor(0xD1D5DB, 0x374151)),
            BorderFactory.createEmptyBorder(10, 12, 10, 12)
        )
        maximumSize = Dimension(Int.MAX_VALUE, 260)
        preferredSize = Dimension(940, 260)

        ButtonGroup().apply {
            add(tokensToggle)
            add(priceToggle)
        }

        val header = Box.createHorizontalBox().apply {
            add(titleLabel.apply { font = font.deriveFont(java.awt.Font.BOLD, 13f) })
            add(Box.createHorizontalGlue())
            add(tokensToggle)
            add(priceToggle)
            add(Box.createHorizontalStrut(14))
            add(colorSwatch(TOKEN_USAGE_CLAUDE_COLOR))
            add(Box.createHorizontalStrut(6))
            add(claudeToggle)
            add(Box.createHorizontalStrut(12))
            add(colorSwatch(TOKEN_USAGE_CODEX_COLOR))
            add(Box.createHorizontalStrut(6))
            add(codexToggle)
        }
        claudeToggle.addActionListener {
            chart.setSeriesVisibility(showClaude = claudeToggle.isSelected, showCodex = codexToggle.isSelected)
        }
        codexToggle.addActionListener {
            chart.setSeriesVisibility(showClaude = claudeToggle.isSelected, showCodex = codexToggle.isSelected)
        }
        tokensToggle.addActionListener { setMetric(TokenUsageChartMetric.Tokens) }
        priceToggle.addActionListener { setMetric(TokenUsageChartMetric.Price) }

        add(header, BorderLayout.NORTH)
        add(chart, BorderLayout.CENTER)
    }

    fun update(claudeRows: List<TokenUsageRow>, codexRows: List<TokenUsageRow>) {
        chart.update(
            claudeRows.toMonthlyValues(),
            codexRows.toMonthlyValues()
        )
    }

    private fun setMetric(metric: TokenUsageChartMetric) {
        titleLabel.text = when (metric) {
            TokenUsageChartMetric.Tokens -> "Monthly total tokens"
            TokenUsageChartMetric.Price -> "Monthly estimated price"
        }
        chart.setMetric(metric)
    }

    private fun List<TokenUsageRow>.toMonthlyValues(): Map<String, TokenUsageMonthlyValue> {
        return filterNot { it.isTotal }
            .associate { row -> row.month to TokenUsageMonthlyValue(tokens = row.totalTokens, priceUsd = row.priceUsd) }
    }

    private fun colorSwatch(color: Color): JPanel {
        return JPanel().apply {
            background = color
            isOpaque = true
            preferredSize = Dimension(12, 12)
            maximumSize = Dimension(12, 12)
            border = BorderFactory.createLineBorder(JBColor(0xFFFFFF, 0x111827))
        }
    }
}

private class TokenUsageChart : JPanel() {
    private val compactFormat = NumberFormat.getCompactNumberInstance(Locale.US, NumberFormat.Style.SHORT)
    private val integerFormat = NumberFormat.getIntegerInstance(Locale.US)
    private val priceFormat = NumberFormat.getCurrencyInstance(Locale.US).apply {
        maximumFractionDigits = 2
        minimumFractionDigits = 2
    }
    private var claudeByMonth: Map<String, TokenUsageMonthlyValue> = emptyMap()
    private var codexByMonth: Map<String, TokenUsageMonthlyValue> = emptyMap()
    private var showClaude = true
    private var showCodex = true
    private var metric = TokenUsageChartMetric.Tokens
    private var barHitboxes: List<TokenUsageBarHitbox> = emptyList()

    init {
        preferredSize = Dimension(900, 200)
        minimumSize = Dimension(400, 180)
        background = JBColor(0xF9FAFB, 0x111827)
        compactFormat.maximumFractionDigits = 1
        ToolTipManager.sharedInstance().registerComponent(this)
    }

    fun update(
        claudeByMonth: Map<String, TokenUsageMonthlyValue>,
        codexByMonth: Map<String, TokenUsageMonthlyValue>
    ) {
        this.claudeByMonth = claudeByMonth
        this.codexByMonth = codexByMonth
        repaint()
    }

    fun setSeriesVisibility(showClaude: Boolean, showCodex: Boolean) {
        this.showClaude = showClaude
        this.showCodex = showCodex
        repaint()
    }

    fun setMetric(metric: TokenUsageChartMetric) {
        this.metric = metric
        repaint()
    }

    override fun paintComponent(graphics: Graphics) {
        super.paintComponent(graphics)
        val g = graphics.create() as Graphics2D
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            paintChart(g)
        } finally {
            g.dispose()
        }
    }

    override fun getToolTipText(event: MouseEvent): String? {
        val hitbox = barHitboxes.firstOrNull { it.bounds.contains(event.point) } ?: return null
        val parts = buildList {
            if (showClaude) add("Claude: ${formatTooltipValue(claudeByMonth[hitbox.month].metricValue())}")
            if (showCodex) add("Codex: ${formatTooltipValue(codexByMonth[hitbox.month].metricValue())}")
        }
        return "${hitbox.month} | ${parts.joinToString(" | ")}"
    }

    private fun paintChart(g: Graphics2D) {
        val months = (claudeByMonth.keys + codexByMonth.keys).sorted()
        if (months.isEmpty() || (!showClaude && !showCodex)) {
            barHitboxes = emptyList()
            paintEmptyState(g, if (months.isEmpty()) "No monthly usage data" else "Select a provider to show the chart")
            return
        }

        val left = 72
        val right = width - 24
        val top = 18
        val bottom = height - 34
        if (right <= left || bottom <= top) return

        val visibleValues = buildList {
            if (showClaude) addAll(months.map { claudeByMonth[it].metricValue() })
            if (showCodex) addAll(months.map { codexByMonth[it].metricValue() })
        }
        val maxValue = visibleValues.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0

        paintGrid(g, left, right, top, bottom, maxValue)
        barHitboxes = paintBars(g, months, left, right, top, bottom, maxValue)
        paintXAxis(g, months, left, right, bottom)
    }

    private fun paintGrid(g: Graphics2D, left: Int, right: Int, top: Int, bottom: Int, maxValue: Double) {
        g.font = g.font.deriveFont(11f)
        g.color = JBColor(0xE5E7EB, 0x374151)
        val steps = 4
        repeat(steps + 1) { index ->
            val ratio = index / steps.toDouble()
            val y = bottom - ((bottom - top) * ratio).toInt()
            g.drawLine(left, y, right, y)
            g.color = JBColor(0x6B7280, 0x9CA3AF)
            g.drawString(formatAxisValue(maxValue * ratio), 8, y + 4)
            g.color = JBColor(0xE5E7EB, 0x374151)
        }
        g.color = JBColor(0x9CA3AF, 0x4B5563)
        g.drawLine(left, top, left, bottom)
        g.drawLine(left, bottom, right, bottom)
    }

    private fun paintBars(
        g: Graphics2D,
        months: List<String>,
        left: Int,
        right: Int,
        top: Int,
        bottom: Int,
        maxValue: Double
    ): List<TokenUsageBarHitbox> {
        val visibleSeries = buildList {
            if (showClaude) add(TokenUsageSeries("Claude", claudeByMonth, CLAUDE_COLOR))
            if (showCodex) add(TokenUsageSeries("Codex", codexByMonth, CODEX_COLOR))
        }
        if (visibleSeries.isEmpty()) return emptyList()

        val groupWidth = ((right - left) / months.size.toDouble()).coerceAtLeast(18.0)
        val totalBarWidth = (groupWidth * 0.68).coerceAtMost(54.0)
        val barGap = if (visibleSeries.size > 1) 4.0 else 0.0
        val barWidth = ((totalBarWidth - barGap * (visibleSeries.size - 1)) / visibleSeries.size)
            .coerceAtLeast(4.0)
        val hitboxes = mutableListOf<TokenUsageBarHitbox>()

        months.forEachIndexed { monthIndex, month ->
            val groupCenter = left + groupWidth * (monthIndex + 0.5)
            val startX = groupCenter - totalBarWidth / 2
            visibleSeries.forEachIndexed { seriesIndex, series ->
                val value = series.values[month].metricValue()
                val barHeight = ((bottom - top) * (value / maxValue)).toInt()
                val x = (startX + seriesIndex * (barWidth + barGap)).toInt()
                val y = bottom - barHeight
                val width = barWidth.toInt().coerceAtLeast(4)
                val height = barHeight.coerceAtLeast(1)
                g.color = series.color
                g.fillRoundRect(x, y, width, height, 6, 6)
                hitboxes += TokenUsageBarHitbox(
                    provider = series.provider,
                    month = month,
                    value = value,
                    bounds = Rectangle(x, y, width, height)
                )
            }
        }
        return hitboxes
    }

    private fun paintXAxis(g: Graphics2D, months: List<String>, left: Int, right: Int, bottom: Int) {
        g.font = g.font.deriveFont(11f)
        g.color = JBColor(0x6B7280, 0x9CA3AF)
        val step = (months.size / 6).coerceAtLeast(1)
        val groupWidth = ((right - left) / months.size.toDouble()).coerceAtLeast(18.0)
        months.forEachIndexed { index, month ->
            if (index % step != 0 && index != months.lastIndex) return@forEachIndexed
            val x = (left + groupWidth * (index + 0.5)).toInt()
            val label = month.removePrefix("20")
            val labelWidth = g.fontMetrics.stringWidth(label)
            g.drawString(label, x - labelWidth / 2, bottom + 22)
        }
    }

    private fun paintEmptyState(g: Graphics2D, message: String) {
        g.color = JBColor(0x6B7280, 0x9CA3AF)
        g.font = g.font.deriveFont(13f)
        val width = g.fontMetrics.stringWidth(message)
        g.drawString(message, (this.width - width) / 2, this.height / 2)
    }

    private fun TokenUsageMonthlyValue?.metricValue(): Double {
        val value = this ?: return 0.0
        return when (metric) {
            TokenUsageChartMetric.Tokens -> value.tokens.toDouble()
            TokenUsageChartMetric.Price -> value.priceUsd
        }
    }

    private fun formatAxisValue(value: Double): String {
        return when (metric) {
            TokenUsageChartMetric.Tokens -> compactFormat.format(value.toLong())
            TokenUsageChartMetric.Price -> if (value < 1_000) {
                priceFormat.format(value)
            } else {
                "$${compactFormat.format(value)}"
            }
        }
    }

    private fun formatTooltipValue(value: Double): String {
        return when (metric) {
            TokenUsageChartMetric.Tokens -> "${integerFormat.format(value.toLong())} tokens"
            TokenUsageChartMetric.Price -> priceFormat.format(value)
        }
    }

    private companion object {
        val CLAUDE_COLOR = TOKEN_USAGE_CLAUDE_COLOR
        val CODEX_COLOR = TOKEN_USAGE_CODEX_COLOR
    }
}

private enum class TokenUsageChartMetric {
    Tokens,
    Price
}

private data class TokenUsageMonthlyValue(
    val tokens: Long,
    val priceUsd: Double
)

private data class TokenUsageSeries(
    val provider: String,
    val values: Map<String, TokenUsageMonthlyValue>,
    val color: Color
)

private data class TokenUsageBarHitbox(
    val provider: String,
    val month: String,
    val value: Double,
    val bounds: Rectangle
)

private val TOKEN_USAGE_CLAUDE_COLOR = JBColor(0xD97706, 0xFBBF24)
private val TOKEN_USAGE_CODEX_COLOR = JBColor(0x2563EB, 0x60A5FA)

private class TokenUsageNumberRenderer(private val model: TokenUsageTableModel) : DefaultTableCellRenderer() {
    private val numberFormat = NumberFormat.getIntegerInstance(Locale.US)

    init {
        horizontalAlignment = SwingConstants.RIGHT
        foreground = JBColor(0x111827, 0xE5E7EB)
    }

    override fun setValue(value: Any?) {
        text = if (value is Number) numberFormat.format(value.toLong()) else value?.toString().orEmpty()
    }

    override fun getTableCellRendererComponent(
        table: JTable,
        value: Any?,
        isSelected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int
    ): java.awt.Component {
        val component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
        val modelRow = table.convertRowIndexToModel(row)
        font = font.deriveFont(if (model.isTotalRow(modelRow)) java.awt.Font.BOLD else java.awt.Font.PLAIN)
        return component
    }
}

private class TokenUsagePriceRenderer(private val model: TokenUsageTableModel) : DefaultTableCellRenderer() {
    private val priceFormat = NumberFormat.getCurrencyInstance(Locale.US).apply {
        maximumFractionDigits = 2
        minimumFractionDigits = 2
    }

    init {
        horizontalAlignment = SwingConstants.RIGHT
        foreground = JBColor(0x111827, 0xE5E7EB)
    }

    override fun setValue(value: Any?) {
        text = if (value is Number) priceFormat.format(value.toDouble()) else value?.toString().orEmpty()
    }

    override fun getTableCellRendererComponent(
        table: JTable,
        value: Any?,
        isSelected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int
    ): java.awt.Component {
        val component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
        val modelRow = table.convertRowIndexToModel(row)
        font = font.deriveFont(if (model.isTotalRow(modelRow)) java.awt.Font.BOLD else java.awt.Font.PLAIN)
        val fallbackCount = model.fallbackPriceCount(modelRow)
        if (fallbackCount > 0) {
            text = "$text *"
            toolTipText = "Estimated with latest known pricing for $fallbackCount usage records"
        } else {
            toolTipText = null
        }
        return component
    }
}

private class TokenUsageTextRenderer(private val model: TokenUsageTableModel) : DefaultTableCellRenderer() {
    override fun getTableCellRendererComponent(
        table: JTable,
        value: Any?,
        isSelected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int
    ): java.awt.Component {
        val component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
        val modelRow = table.convertRowIndexToModel(row)
        font = font.deriveFont(if (model.isTotalRow(modelRow)) java.awt.Font.BOLD else java.awt.Font.PLAIN)
        foreground = JBColor(0x111827, 0xE5E7EB)
        return component
    }
}
