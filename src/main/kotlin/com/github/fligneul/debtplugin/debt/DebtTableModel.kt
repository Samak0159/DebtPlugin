package com.github.fligneul.debtplugin.debt

import javax.swing.table.DefaultTableModel

class DebtTableModel(private val debtService: DebtService) : DefaultTableModel() {

    val debtItems = mutableListOf<DebtItem>() // Internal list to store DebtItem objects

    init {
        addColumn("File") // 0
        addColumn("Line") // 1
        addColumn("Description") // 2
        addColumn("Status") // 3
        addColumn("Priority") // 4
        addColumn("User") // 5
        addColumn("Comment") // 6
        addColumn("") // 7 action (delete)
    }

    override fun isCellEditable(row: Int, column: Int): Boolean {
        // Make the first two columns (File and Line) non-editable
        // Also keep the User column (5) non-editable; Delete button column (7) must be editable for the button
        return column >= 2 && column != 5
    }

    override fun getColumnClass(columnIndex: Int): Class<*> {
        return when (columnIndex) {
            3 -> Status::class.java // Status column
            4 -> Priority::class.java // Priority column
            7 -> Any::class.java // Action column (for button)
            else -> super.getColumnClass(columnIndex)
        }
    }

    override fun setValueAt(aValue: Any?, row: Int, column: Int) {
        val oldDebtItem = debtItems[row]
        val updatedDebtItem = when (column) {
            2 -> oldDebtItem.copy(description = aValue as String)
            3 -> oldDebtItem.copy(status = aValue as Status)
            4 -> oldDebtItem.copy(priority = aValue as Priority)
            6 -> oldDebtItem.copy(comment = aValue as String)
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
        addRow(arrayOf(
            displayedFile,
            debtItem.line,
            debtItem.description,
            debtItem.status,
            debtItem.priority,
            debtItem.username ?: "",
            debtItem.comment,
            null
        )) // Add only visible data to DefaultTableModel
    }

    // Method to clear all debt items
    fun clearAll() {
        debtItems.clear()
        rowCount = 0 // Clear rows in DefaultTableModel
    }
}