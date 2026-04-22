package com.githeatmap.ui

import com.githeatmap.model.AuthorFileMetrics
import com.githeatmap.model.AuthorMetrics
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JTable
import javax.swing.SortOrder
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableRowSorter

class AuthorStatsPanel(
    private val onAuthorActivated: (String, List<AuthorFileMetrics>) -> Unit = { _, _ -> }
) : JTable(AuthorTableModel()) {

    init {
        val sorter = TableRowSorter(model).apply {
            setSortable(0, false)
            setComparator(2, compareBy<Int> { it })
            setComparator(3, compareBy<Int> { it })
            setComparator(4, compareBy<Int> { it })
            setComparator(5, compareBy<Int> { it })
            setComparator(6, compareBy<Double> { it })
            setComparator(7, compareBy<Int> { it })
            setComparator(8, compareBy<Int> { it })
            sortKeys = listOf(javax.swing.RowSorter.SortKey(8, SortOrder.DESCENDING))
        }
        rowSorter = sorter

        setDefaultRenderer(Double::class.java, object : DefaultTableCellRenderer() {
            override fun setValue(value: Any?) {
                text = if (value is Double) "%.1f".format(value) else ""
            }
        })

        setDefaultRenderer(Int::class.javaObjectType, object : DefaultTableCellRenderer() {
            override fun setValue(value: Any?) {
                text = when (value) {
                    is Int -> "%,d".format(value)
                    else -> ""
                }
            }
        })

        columnModel.getColumn(0).apply {
            minWidth = 40
            preferredWidth = 45
            maxWidth = 55
        }
        val durationRenderer = object : DefaultTableCellRenderer() {
            override fun setValue(value: Any?) {
                text = if (value is Int) formatDuration(value) else ""
            }
        }
        columnModel.getColumn(7).cellRenderer = durationRenderer
        columnModel.getColumn(8).cellRenderer = durationRenderer

        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(event: MouseEvent) {
                if (event.clickCount == 2) {
                    activateSelectedAuthor()
                }
            }
        })
    }

    fun setData(metrics: List<AuthorMetrics>, detailsByAuthor: Map<String, List<AuthorFileMetrics>>) {
        (model as AuthorTableModel).update(metrics, detailsByAuthor)
        (rowSorter as? TableRowSorter<*>)?.sort()
    }

    override fun getValueAt(row: Int, column: Int): Any {
        return if (column == 0) {
            row + 1
        } else {
            super.getValueAt(row, column)
        }
    }

    private fun activateSelectedAuthor() {
        val selectedViewRow = selectedRow
        if (selectedViewRow < 0) return

        val tableModel = model as AuthorTableModel
        val modelRow = convertRowIndexToModel(selectedViewRow)
        val author = tableModel.authorAt(modelRow) ?: return
        onAuthorActivated(author, tableModel.detailsFor(author))
    }
}

class AuthorTableModel : AbstractTableModel() {
    private var data: List<AuthorMetrics> = emptyList()
    private var detailsByAuthor: Map<String, List<AuthorFileMetrics>> = emptyMap()
    private val columns = arrayOf("#", "Author", "Commits", "Added", "Deleted", "Net", "Avg/Commit", "Effort Min", "Effort Max")

    fun update(metrics: List<AuthorMetrics>, detailsByAuthor: Map<String, List<AuthorFileMetrics>>) {
        data = metrics
        this.detailsByAuthor = detailsByAuthor
        fireTableDataChanged()
    }

    fun authorAt(row: Int): String? = data.getOrNull(row)?.author

    fun detailsFor(author: String): List<AuthorFileMetrics> = detailsByAuthor[author].orEmpty()

    override fun getRowCount() = data.size

    override fun getColumnCount() = columns.size

    override fun getColumnName(col: Int) = columns[col]

    override fun getColumnClass(columnIndex: Int): Class<*> {
        return when (columnIndex) {
            0, 2, 3, 4, 5, 7, 8 -> Int::class.javaObjectType
            6 -> Double::class.javaObjectType
            else -> String::class.java
        }
    }

    override fun getValueAt(row: Int, col: Int): Any {
        val metric = data[row]
        return when (col) {
            0 -> row + 1
            1 -> metric.author
            2 -> metric.commitCount
            3 -> metric.totalAdded
            4 -> metric.totalDeleted
            5 -> metric.netLines
            6 -> metric.avgChangesPerCommit
            7 -> metric.effortMinMinutes
            8 -> metric.effortMaxMinutes
            else -> ""
        }
    }
}

private fun formatDuration(minutes: Int): String {
    return when {
        minutes >= 8 * 60 -> "%.1fd".format(minutes / 480.0)
        minutes >= 60 -> "%.1fh".format(minutes / 60.0)
        else -> "${minutes}m"
    }
}
