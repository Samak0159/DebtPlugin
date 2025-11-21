package com.github.fligneul.debtplugin.debt

import com.github.fligneul.debtplugin.settings.DebtSettings
import com.github.fligneul.debtplugin.settings.DebtSettingsListener
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import java.awt.BorderLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.DefaultCellEditor
import javax.swing.JComboBox
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.table.DefaultTableModel

class DebtToolWindow(private val project: Project) {

    private val debtService = project.service<DebtService>()
    private val tableModel = DebtTableModel(debtService)
    private val table = JBTable(tableModel)

    init {
        val complexityComboBox = JComboBox(Complexity.values())
        table.columnModel.getColumn(6).cellEditor = DefaultCellEditor(complexityComboBox)

        val statusComboBox = JComboBox(Status.values())
        table.columnModel.getColumn(7).cellEditor = DefaultCellEditor(statusComboBox)

        val priorityComboBox = JComboBox(Priority.values())
        table.columnModel.getColumn(8).cellEditor = DefaultCellEditor(priorityComboBox)

        val riskComboBox = JComboBox(Risk.values())
        table.columnModel.getColumn(9).cellEditor = DefaultCellEditor(riskComboBox)

        // Delete button column is at index 12
        table.columnModel.getColumn(12).cellRenderer = DeleteButtonCell { row ->
            val debtItem = tableModel.debtItems[row]
            debtService.remove(debtItem)
            updateTable()
        }
        table.columnModel.getColumn(12).cellEditor = DeleteButtonCell { row ->
            val debtItem = tableModel.debtItems[row]
            debtService.remove(debtItem)
            updateTable()
        }
        table.columnModel.getColumn(12).preferredWidth = 30
        table.columnModel.getColumn(12).maxWidth = 30
        table.columnModel.getColumn(12).minWidth = 30

        // Refresh the table automatically when settings change (e.g., username updated)
        project.messageBus.connect().subscribe(DebtSettings.TOPIC, object : DebtSettingsListener {
            override fun settingsChanged(settings: DebtSettings.State) {
                updateTable()
            }
        })

        val debtFile = LocalFileSystem.getInstance().findFileByIoFile(debtService.debtFile)
    }

    fun getContent(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.add(JBScrollPane(table), BorderLayout.CENTER)

        table.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    val row = table.selectedRow
                    if (row >= 0) {
                        val file = tableModel.debtItems[row].file
                        val line = table.getValueAt(row, 1) as Int
                        val virtualFile = LocalFileSystem.getInstance().findFileByPath(file)
                        if (virtualFile != null) {
                            val descriptor = OpenFileDescriptor(project, virtualFile, line - 1, 0)
                            FileEditorManager.getInstance(project).openTextEditor(descriptor, true)
                        }
                    }
                }
            }
        })

        val refreshButton = JButton("Refresh")
        refreshButton.addActionListener {
            updateTable()
        }
        panel.add(refreshButton, BorderLayout.SOUTH)

        updateTable()

        return panel
    }

    private fun updateTable() {
        tableModel.clearAll()
        debtService.all().forEach {
            tableModel.addDebtItem(it)
        }
    }
}
