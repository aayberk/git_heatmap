package com.githeatmap.ui

import com.githeatmap.engine.ScoredFile
import com.githeatmap.model.AuthorFileMetrics
import com.githeatmap.model.CommitEvent
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JTable
import javax.swing.ListSelectionModel
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableRowSorter

class FileListPanel(private val project: Project) : JTable(FileListTableModel()) {
    var repositoryRootPath: String? = project.basePath
    var absolutePathResolver: (String) -> String? = { path -> repositoryRootPath?.let { "$it/$path" } }

    init {
        fillsViewportHeight = true
        selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION

        val sorter = TableRowSorter(model).apply {
            setComparator(1, compareBy<Double> { it })
            setComparator(2, compareBy<Int> { it })
            setComparator(3, compareBy<Int> { it })
            setComparator(4, compareBy<Int> { it })
            setComparator(5, compareBy<Int> { it })
            setComparator(6, compareBy<Int> { it })
        }
        rowSorter = sorter

        setDefaultRenderer(Double::class.java, object : DefaultTableCellRenderer() {
            override fun setValue(value: Any?) {
                text = if (value is Double) "%.2f".format(value) else ""
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

        columnModel.getColumn(1).apply {
            minWidth = 70
            preferredWidth = 80
            maxWidth = 90
        }
        columnModel.getColumn(2).apply {
            minWidth = 70
            preferredWidth = 80
            maxWidth = 90
        }
        columnModel.getColumn(3).apply {
            minWidth = 80
            preferredWidth = 90
            maxWidth = 110
        }
        columnModel.getColumn(4).apply {
            minWidth = 80
            preferredWidth = 90
            maxWidth = 110
        }
        columnModel.getColumn(5).apply {
            minWidth = 80
            preferredWidth = 90
            maxWidth = 110
        }
        columnModel.getColumn(6).apply {
            minWidth = 70
            preferredWidth = 80
            maxWidth = 90
        }

        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(event: MouseEvent) {
                if (event.clickCount == 2) {
                    openSelectedFile()
                }
            }
        })
    }

    fun setData(files: List<ScoredFile>) {
        (model as FileListTableModel).update(files)
        (rowSorter as? TableRowSorter<*>)?.sort()
    }

    fun setOverlayPaths(paths: Set<String>) {
        (model as FileListTableModel).setOverlayPaths(paths)
    }

    fun setAuthorFilter(author: String, details: List<AuthorFileMetrics>) {
        (model as FileListTableModel).setAuthorFilter(author, details)
        (rowSorter as? TableRowSorter<*>)?.sort()
    }

    fun clearAuthorFilter() {
        (model as FileListTableModel).clearAuthorFilter()
        (rowSorter as? TableRowSorter<*>)?.sort()
    }

    fun setCommitFilter(commit: CommitEvent) {
        (model as FileListTableModel).setCommitFilter(commit)
        (rowSorter as? TableRowSorter<*>)?.sort()
    }

    fun clearCommitFilter() {
        (model as FileListTableModel).clearCommitFilter()
        (rowSorter as? TableRowSorter<*>)?.sort()
    }

    fun activeAuthorFilter(): String? = (model as FileListTableModel).activeAuthorFilter()
    fun activeCommitFilter(): String? = (model as FileListTableModel).activeCommitFilter()

    fun selectPath(path: String?) {
        if (path == null) {
            clearSelection()
            return
        }

        val tableModel = model as FileListTableModel
        val row = tableModel.indexOf(path)
        if (row < 0) {
            clearSelection()
            return
        }

        val viewRow = convertRowIndexToView(row)
        if (viewRow >= 0) {
            selectionModel.setSelectionInterval(viewRow, viewRow)
            scrollRectToVisible(getCellRect(viewRow, 0, true))
        }
    }

    fun resolveAbsolutePath(filePath: String): String? = absolutePathResolver(filePath)

    private fun openSelectedFile() {
        val selectedViewRow = selectedRow
        if (selectedViewRow < 0) return

        val tableModel = model as FileListTableModel
        val filePath = tableModel.filePathAt(convertRowIndexToModel(selectedViewRow))
        val absolutePath = resolveAbsolutePath(filePath) ?: return
        val virtualFile = LocalFileSystem.getInstance().findFileByPath(absolutePath) ?: return

        FileEditorManager.getInstance(project).openFile(virtualFile, true)
    }
}

private class FileListTableModel : AbstractTableModel() {
    private val columns = arrayOf("File", "Heat", "Commits", "Added", "Deleted", "Net", "Authors")
    private var allRows: List<FileRow> = emptyList()
    private var filteredRows: List<FileRow> = emptyList()
    private var rowsByPath: Map<String, FileRow> = emptyMap()
    private var overlayPaths: Set<String> = emptySet()
    private var authorFilterRows: List<FileRow>? = null
    private var authorFilter: String? = null
    private var commitFilterRows: List<FileRow>? = null
    private var commitFilterHash: String? = null

    fun update(files: List<ScoredFile>) {
        allRows = files.map {
            FileRow(
                path = it.metrics.path,
                heatScore = it.heatScore,
                commitCount = it.metrics.commitCount,
                totalAdded = it.metrics.totalAdded,
                totalDeleted = it.metrics.totalDeleted,
                authorCount = it.metrics.authors.size
            )
        }
        rowsByPath = allRows.associateBy { it.path }

        authorFilterRows = authorFilterRows?.mapNotNull { filtered ->
            rowsByPath[filtered.path]?.let { base ->
                filtered.copy(
                    heatScore = base.heatScore,
                    authorCount = base.authorCount
                )
            }
        }
        if (authorFilterRows?.isEmpty() == true) {
            authorFilterRows = null
            authorFilter = null
        }
        commitFilterRows = commitFilterRows?.mapNotNull { filtered ->
            rowsByPath[filtered.path]?.let { base ->
                filtered.copy(
                    heatScore = base.heatScore,
                    authorCount = base.authorCount
                )
            } ?: filtered
        }
        if (commitFilterRows?.isEmpty() == true) {
            commitFilterRows = null
            commitFilterHash = null
        }

        applyFilter()
    }

    fun setOverlayPaths(paths: Set<String>) {
        overlayPaths = paths
        applyFilter()
    }

    fun setAuthorFilter(author: String, details: List<AuthorFileMetrics>) {
        authorFilter = author
        authorFilterRows = details.map { detail ->
            val base = rowsByPath[detail.path]
            FileRow(
                path = detail.path,
                heatScore = base?.heatScore ?: 0.0,
                commitCount = detail.commitCount,
                totalAdded = detail.totalAdded,
                totalDeleted = detail.totalDeleted,
                authorCount = base?.authorCount ?: 1
            )
        }
        applyFilter()
    }

    fun clearAuthorFilter() {
        authorFilter = null
        authorFilterRows = null
        applyFilter()
    }

    fun activeAuthorFilter(): String? = authorFilter
    fun activeCommitFilter(): String? = commitFilterHash

    fun setCommitFilter(commit: CommitEvent) {
        commitFilterHash = commit.hash
        commitFilterRows = commit.files.map { change ->
            val base = rowsByPath[change.path]
            FileRow(
                path = change.path,
                heatScore = base?.heatScore ?: 0.0,
                commitCount = 1,
                totalAdded = change.addedLines,
                totalDeleted = change.deletedLines,
                authorCount = 1
            )
        }
        applyFilter()
    }

    fun clearCommitFilter() {
        commitFilterHash = null
        commitFilterRows = null
        applyFilter()
    }

    private fun applyFilter() {
        val sourceRows = commitFilterRows ?: authorFilterRows ?: allRows
        filteredRows = if (overlayPaths.isEmpty()) {
            sourceRows
        } else {
            sourceRows.filter { it.path in overlayPaths }
        }
        fireTableDataChanged()
    }

    fun indexOf(path: String): Int = filteredRows.indexOfFirst { it.path == path }

    fun filePathAt(row: Int): String = filteredRows[row].path

    override fun getRowCount(): Int = filteredRows.size

    override fun getColumnCount(): Int = columns.size

    override fun getColumnName(column: Int): String = columns[column]

    override fun getColumnClass(columnIndex: Int): Class<*> {
        return when (columnIndex) {
            1 -> Double::class.javaObjectType
            2, 3, 4, 5, 6 -> Int::class.javaObjectType
            else -> String::class.java
        }
    }

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        val row = filteredRows[rowIndex]
        return when (columnIndex) {
            0 -> row.path
            1 -> row.heatScore
            2 -> row.commitCount
            3 -> row.totalAdded
            4 -> row.totalDeleted
            5 -> row.netLines
            6 -> row.authorCount
            else -> ""
        }
    }

    private data class FileRow(
        val path: String,
        val heatScore: Double,
        val commitCount: Int,
        val totalAdded: Int,
        val totalDeleted: Int,
        val authorCount: Int
    ) {
        val netLines: Int
            get() = totalAdded - totalDeleted
    }
}
