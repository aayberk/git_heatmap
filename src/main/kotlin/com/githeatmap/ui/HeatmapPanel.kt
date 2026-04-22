package com.githeatmap.ui

import com.githeatmap.engine.ScoredFile
import com.intellij.util.ui.UIUtil
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JPanel

class HeatmapPanel : JPanel() {

    var files: List<ScoredFile> = emptyList()
        set(value) {
            field = value
            rebuildCells()
        }

    var overlayPaths: Set<String> = emptySet()
        set(value) {
            field = value
            repaint()
        }

    var selectedPath: String? = null
        set(value) {
            field = value
            repaint()
        }

    private var cells: List<HeatmapCell> = emptyList()
    private val cellSize = 14
    private val gap = 4
    private val padding = 14
    private val headerHeight = 38

    private data class HeatmapCell(
        val file: ScoredFile,
        val bounds: Rectangle
    )

    private data class HeatmapPalette(
        val background: Color,
        val title: Color,
        val subtitle: Color,
        val cellBorder: Color,
        val selectionStroke: Color,
        val overlayColor: Color,
        val overlayStroke: Color,
        val emptyState: Color,
        val heatStops: List<Pair<Double, Color>>
    )

    init {
        addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) {
                rebuildCells()
            }
        })
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val graphics = g as Graphics2D
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)

        paintBackground(graphics)
        paintLegend(graphics)

        if (cells.isEmpty()) {
            paintEmptyState(graphics)
            return
        }

        val palette = currentPalette()
        cells.forEach { cell ->
            val isInOverlay = cell.file.metrics.path in overlayPaths
            val isSelected = cell.file.metrics.path == selectedPath
            val baseColor = if (isInOverlay) palette.overlayColor else heatToColor(cell.file.heatScore)
            graphics.color = baseColor
            graphics.fillRect(cell.bounds.x, cell.bounds.y, cell.bounds.width, cell.bounds.height)

            graphics.color = palette.cellBorder
            graphics.stroke = BasicStroke(1f)
            graphics.drawRect(cell.bounds.x, cell.bounds.y, cell.bounds.width, cell.bounds.height)

            if (isInOverlay) {
                graphics.color = palette.overlayStroke
                graphics.stroke = BasicStroke(1.6f)
                graphics.drawRect(cell.bounds.x, cell.bounds.y, cell.bounds.width, cell.bounds.height)
            }

            if (isSelected) {
                graphics.color = palette.selectionStroke
                graphics.stroke = BasicStroke(2f)
                graphics.drawRect(
                    cell.bounds.x - 2,
                    cell.bounds.y - 2,
                    cell.bounds.width + 4,
                    cell.bounds.height + 4
                )
            }
        }
    }

    private fun rebuildCells() {
        cells = calculateCells()
        revalidate()
        repaint()
    }

    private fun calculateCells(): List<HeatmapCell> {
        if (files.isEmpty()) return emptyList()

        val cols = calculateColumnCount()

        return files.mapIndexed { index, file ->
            val row = index / cols
            val col = index % cols
            HeatmapCell(
                file = file,
                bounds = Rectangle(
                    padding + col * (cellSize + gap),
                    headerHeight + padding + row * (cellSize + gap),
                    cellSize,
                    cellSize
                )
            )
        }
    }

    private fun calculateColumnCount(): Int {
        val availableWidth = (width.takeIf { it > 0 } ?: preferredViewportWidth()) - (padding * 2)
        return maxOf(1, availableWidth / (cellSize + gap))
    }

    private fun preferredViewportWidth(): Int = 640

    private fun heatToColor(heat: Double): Color {
        val normalized = heat.coerceIn(0.0, 1.0)
        val colorStops = currentPalette().heatStops

        val upperIndex = colorStops.indexOfFirst { normalized <= it.first }.coerceAtLeast(1)
        val (startStop, startColor) = colorStops[upperIndex - 1]
        val (endStop, endColor) = colorStops[upperIndex]
        val localRatio = ((normalized - startStop) / (endStop - startStop)).coerceIn(0.0, 1.0)

        return interpolateColor(startColor, endColor, localRatio)
    }

    fun getFileAt(x: Int, y: Int): ScoredFile? {
        return cells.find { it.bounds.contains(x, y) }?.file
    }

    override fun getPreferredSize(): Dimension {
        val cols = calculateColumnCount()
        val rows = if (files.isEmpty()) 1 else ((files.size - 1) / cols) + 1
        val height = headerHeight + padding * 2 + rows * (cellSize + gap)
        return Dimension(preferredViewportWidth(), maxOf(200, height))
    }

    private fun paintBackground(graphics: Graphics2D) {
        val palette = currentPalette()
        graphics.color = palette.background
        graphics.fillRect(0, 0, width, height)
    }

    private fun paintLegend(graphics: Graphics2D) {
        val palette = currentPalette()
        graphics.color = palette.title
        graphics.font = graphics.font.deriveFont(12f)
        graphics.drawString("Repository Heatmap", padding, 18)

        graphics.color = palette.subtitle
        graphics.font = graphics.font.deriveFont(10f)
        graphics.drawString("cool", padding, 31)

        val legendX = padding + 32
        val legendY = 22
        val legendWidth = 160
        val legendHeight = 8
        val segments = 28
        val segmentWidth = legendWidth.toDouble() / segments
        for (segment in 0 until segments) {
            val ratio = segment.toDouble() / (segments - 1)
            graphics.color = heatToColor(ratio)
            graphics.fillRect(
                (legendX + segment * segmentWidth).toInt(),
                legendY,
                segmentWidth.toInt() + 1,
                legendHeight
            )
        }

        graphics.color = palette.subtitle
        graphics.drawString("hot", legendX + legendWidth + 8, 31)

        if (overlayPaths.isNotEmpty()) {
            graphics.color = palette.overlayColor
            graphics.fillRect(width - 108, 13, 10, 10)
            graphics.color = palette.title
            graphics.drawString("PR overlay", width - 92, 22)
        }
    }

    private fun paintEmptyState(graphics: Graphics2D) {
        graphics.color = currentPalette().emptyState
        graphics.font = graphics.font.deriveFont(13f)
        graphics.drawString("Load repository history to populate the heatmap.", padding, headerHeight + 30)
    }

    private fun currentPalette(): HeatmapPalette {
        return if (UIUtil.isUnderDarcula()) {
            HeatmapPalette(
                background = Color(43, 43, 43),
                title = Color(223, 223, 223),
                subtitle = Color(143, 143, 143),
                cellBorder = Color(58, 63, 70),
                selectionStroke = Color(245, 247, 255, 220),
                overlayColor = Color(98, 75, 201),
                overlayStroke = Color(242, 242, 242, 210),
                emptyState = Color(154, 164, 185),
                heatStops = listOf(
                    0.0 to Color(53, 71, 92),
                    0.25 to Color(70, 126, 196),
                    0.5 to Color(73, 174, 112),
                    0.75 to Color(224, 180, 68),
                    1.0 to Color(210, 92, 75)
                )
            )
        } else {
            HeatmapPalette(
                background = Color(250, 250, 250),
                title = Color(55, 59, 65),
                subtitle = Color(108, 114, 123),
                cellBorder = Color(224, 227, 231),
                selectionStroke = Color(32, 37, 45),
                overlayColor = Color(87, 108, 219),
                overlayStroke = Color(255, 255, 255, 220),
                emptyState = Color(88, 96, 112),
                heatStops = listOf(
                    0.0 to Color(216, 228, 241),
                    0.25 to Color(125, 174, 223),
                    0.5 to Color(112, 193, 133),
                    0.75 to Color(241, 199, 92),
                    1.0 to Color(224, 111, 92)
                )
            )
        }
    }

    private fun interpolateColor(start: Color, end: Color, ratio: Double): Color {
        val red = (start.red + ((end.red - start.red) * ratio)).toInt()
        val green = (start.green + ((end.green - start.green) * ratio)).toInt()
        val blue = (start.blue + ((end.blue - start.blue) * ratio)).toInt()
        return Color(red, green, blue)
    }
}
