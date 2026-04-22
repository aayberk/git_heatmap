package com.githeatmap.ui

import com.githeatmap.model.CommitEffortMetrics
import com.githeatmap.model.CommitEvent
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseEvent as AwtMouseEvent
import javax.swing.JTable
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableRowSorter

class CommitListPanel(
    private val onCommitActivated: (CommitEvent) -> Unit = {}
) : JTable(CommitListTableModel()) {

    init {
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(event: MouseEvent) {
                if (event.clickCount == 2) {
                    val selectedViewRow = selectedRow
                    if (selectedViewRow < 0) return
                    val commit = (model as CommitListTableModel).commitAt(convertRowIndexToModel(selectedViewRow))
                    onCommitActivated(commit)
                }
            }
        })
        autoCreateRowSorter = false
        fillsViewportHeight = true
        autoResizeMode = AUTO_RESIZE_LAST_COLUMN

        val sorter = TableRowSorter(model).apply {
            setComparator(3, compareBy<Long> { it })
            setComparator(4, compareBy<Int> { it })
            setComparator(5, compareBy<Int> { it })
            setComparator(6, compareBy<Int> { it })
            setComparator(7, compareBy<Int> { it })
        }
        rowSorter = sorter

        setDefaultRenderer(Long::class.javaObjectType, object : DefaultTableCellRenderer() {
            override fun setValue(value: Any?) {
                text = if (value is Long) formatTimestamp(value) else ""
            }
        })

        setDefaultRenderer(Int::class.javaObjectType, object : DefaultTableCellRenderer() {
            override fun setValue(value: Any?) {
                text = if (value is Int) {
                    "%,d".format(value)
                } else {
                    ""
                }
            }
        })
        columnModel.getColumn(7).cellRenderer = object : DefaultTableCellRenderer() {
            override fun setValue(value: Any?) {
                text = if (value is Int) formatCommitDuration(value) else ""
            }
        }

        columnModel.getColumn(0).apply {
            minWidth = 90
            preferredWidth = 95
            maxWidth = 110
        }
        columnModel.getColumn(1).apply {
            minWidth = 0
            preferredWidth = 0
            maxWidth = 0
        }
        columnModel.getColumn(2).apply {
            minWidth = 120
            preferredWidth = 140
            maxWidth = 220
        }
        columnModel.getColumn(3).apply {
            minWidth = 130
            preferredWidth = 145
            maxWidth = 170
        }
        columnModel.getColumn(4).apply {
            minWidth = 55
            preferredWidth = 60
            maxWidth = 70
        }
        columnModel.getColumn(5).apply {
            minWidth = 70
            preferredWidth = 80
            maxWidth = 95
        }
        columnModel.getColumn(6).apply {
            minWidth = 70
            preferredWidth = 80
            maxWidth = 95
        }
        columnModel.getColumn(7).apply {
            minWidth = 80
            preferredWidth = 90
            maxWidth = 105
        }
        columnModel.getColumn(8).preferredWidth = 520
    }

    fun setData(commits: List<CommitEvent>, efforts: List<CommitEffortMetrics>) {
        (model as CommitListTableModel).update(commits, efforts)
        (rowSorter as? TableRowSorter<*>)?.sort()
    }

    fun setShowRepositoryColumn(show: Boolean) {
        columnModel.getColumn(1).apply {
            minWidth = if (show) 120 else 0
            preferredWidth = if (show) 140 else 0
            maxWidth = if (show) 220 else 0
            width = preferredWidth
        }
    }

    override fun getToolTipText(event: AwtMouseEvent): String? {
        val viewRow = rowAtPoint(event.point)
        val viewColumn = columnAtPoint(event.point)
        if (viewRow < 0 || viewColumn < 0) return null

        val modelRow = convertRowIndexToModel(viewRow)
        val modelColumn = convertColumnIndexToModel(viewColumn)
        if (modelColumn != 8) return null

        val message = (model as CommitListTableModel).messageAt(modelRow)
        return if (message.isBlank()) null else "<html>${escapeHtml(message)}</html>"
    }

    private fun formatTimestamp(timestamp: Long): String {
        return DATE_FORMATTER.format(Instant.ofEpochSecond(timestamp))
    }

    private companion object {
        val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault())
    }
}

private fun formatCommitDuration(minutes: Int): String {
    return when {
        minutes >= 8 * 60 -> "%.1fd".format(minutes / 480.0)
        minutes >= 60 -> "%.1fh".format(minutes / 60.0)
        else -> "${minutes}m"
    }
}

private class CommitListTableModel : AbstractTableModel() {
    private val columns = arrayOf("Hash", "Repo", "Author", "Date", "Files", "Added", "Deleted", "Effort", "Message")
    private var rows: List<CommitRow> = emptyList()

    fun update(commits: List<CommitEvent>, efforts: List<CommitEffortMetrics>) {
        val effortByCommit = efforts.associateBy { commitKey(it.commitHash, it.repoId) }
        rows = commits.map { commit ->
            val effort = effortByCommit[commitKey(commit.hash, commit.repoId)]?.effort
            CommitRow(
                commit = commit,
                hash = commit.hash,
                repoName = commit.repoName,
                author = commit.author,
                timestamp = commit.timestamp,
                fileCount = commit.files.size,
                totalAdded = commit.files.sumOf { it.addedLines },
                totalDeleted = commit.files.sumOf { it.deletedLines },
                effortMinutes = effort?.maxMinutes ?: 0,
                message = commit.message
            )
        }
        fireTableDataChanged()
    }

    override fun getRowCount(): Int = rows.size

    override fun getColumnCount(): Int = columns.size

    override fun getColumnName(column: Int): String = columns[column]

    override fun getColumnClass(columnIndex: Int): Class<*> {
        return when (columnIndex) {
            3 -> Long::class.javaObjectType
            4, 5, 6, 7 -> Int::class.javaObjectType
            else -> String::class.java
        }
    }

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        val row = rows[rowIndex]
        return when (columnIndex) {
            0 -> row.hash.take(8)
            1 -> row.repoName
            2 -> row.author
            3 -> row.timestamp
            4 -> row.fileCount
            5 -> row.totalAdded
            6 -> row.totalDeleted
            7 -> row.effortMinutes
            8 -> row.message
            else -> ""
        }
    }

    fun commitAt(rowIndex: Int): CommitEvent = rows[rowIndex].commit

    fun messageAt(rowIndex: Int): String = rows[rowIndex].message

    private data class CommitRow(
        val commit: CommitEvent,
        val hash: String,
        val repoName: String,
        val author: String,
        val timestamp: Long,
        val fileCount: Int,
        val totalAdded: Int,
        val totalDeleted: Int,
        val effortMinutes: Int,
        val message: String
    )

    private fun commitKey(hash: String, repoId: String): String = "$repoId::$hash"
}

private fun escapeHtml(value: String): String {
    return buildString(value.length) {
        value.forEach { char ->
            append(
                when (char) {
                    '&' -> "&amp;"
                    '<' -> "&lt;"
                    '>' -> "&gt;"
                    '"' -> "&quot;"
                    '\'' -> "&#39;"
                    '\n' -> "<br/>"
                    else -> char
                }
            )
        }
    }
}
