package com.github.fligneul.debtplugin.debt

import javax.swing.table.DefaultTableModel

class DebtTableModel(private val debtService: DebtService) : DefaultTableModel() {

    val debtItems = mutableListOf<DebtItem>() // Internal list to store DebtItem objects

    init {
        addColumn("File")
        addColumn("Line")
        addColumn("Description")
        addColumn("Status")
        addColumn("Priority")
        addColumn("Comment")
        addColumn("")
    }

    override fun isCellEditable(row: Int, column: Int): Boolean {
        // Make the first two columns (File and Line) non-editable
        return column >= 2
    }

    override fun getColumnClass(columnIndex: Int): Class<*> {
        return when (columnIndex) {
            3 -> Status::class.java // Status column
            4 -> Priority::class.java // Priority column
            6 -> Any::class.java // Action column (for button)
            else -> super.getColumnClass(columnIndex)
        }
    }

    override fun setValueAt(aValue: Any?, row: Int, column: Int) {
        val oldDebtItem = debtItems[row]
        val updatedDebtItem = when (column) {
            2 -> oldDebtItem.copy(description = aValue as String)
            3 -> oldDebtItem.copy(status = aValue as Status)
            4 -> oldDebtItem.copy(priority = aValue as Priority)
            5 -> oldDebtItem.copy(comment = aValue as String)
            else -> oldDebtItem
        }

        if (updatedDebtItem != oldDebtItem) {
            debtItems[row] = updatedDebtItem
            debtService.update(oldDebtItem, updatedDebtItem)
            super.setValueAt(aValue, row, column)
        }
    }

    // Method to add DebtItem directly
    fun addDebtItem(debtItem: DebtItem) {
        debtItems.add(debtItem) // Add to internal list
        val displayedFile = debtItem.file.substringAfterLast('\\').substringAfterLast('/')
        addRow(arrayOf(displayedFile, debtItem.line, debtItem.description, debtItem.status, debtItem.priority, debtItem.comment)) // Add only visible data to DefaultTableModel
    }

    // Method to clear all debt items
    fun clearAll() {
        debtItems.clear()
        rowCount = 0 // Clear rows in DefaultTableModel
    }
}