package com.github.fligneul.debtplugin.debt

import javax.swing.table.DefaultTableModel

class DebtTableModel(private val debtService: DebtService) : DefaultTableModel() {

    val debtItems = mutableListOf<DebtItem>() // Internal list to store DebtItem objects

    init {
        addColumn("File") // 0
        addColumn("Line") // 1
        addColumn("Title") // 2
        addColumn("Description") // 3
        addColumn("User") // 4
        addColumn("WantedLevel") // 5
        addColumn("Complexity") // 6
        addColumn("Status") // 7
        addColumn("Priority") // 8
        addColumn("Risk") // 9
        addColumn("TargetVersion") // 10
        addColumn("Comment") // 11
        addColumn("") // 12 action (delete)
    }

    override fun isCellEditable(row: Int, column: Int): Boolean {
        // Make the first two columns (File and Line) non-editable
        // Keep the User column (4) non-editable; Delete button column (12) must be editable for the button
        return (column >= 2) && (column != 4)
    }

    override fun getColumnClass(columnIndex: Int): Class<*> {
        return when (columnIndex) {
            5 -> Int::class.java // WantedLevel column
            6 -> Complexity::class.java // Complexity column
            7 -> Status::class.java // Status column
            8 -> Priority::class.java // Priority column
            9 -> Risk::class.java // Risk column
            12 -> Any::class.java // Action column (for button)
            else -> super.getColumnClass(columnIndex)
        }
    }

    override fun setValueAt(aValue: Any?, row: Int, column: Int) {
        val oldDebtItem = debtItems[row]
        val updatedDebtItem = when (column) {
            2 -> oldDebtItem.copy(title = aValue as String)
            3 -> oldDebtItem.copy(description = aValue as String)
            5 -> {
                val asInt = when (aValue) {
                    is Number -> aValue.toInt()
                    is String -> aValue.toIntOrNull() ?: oldDebtItem.wantedLevel
                    else -> oldDebtItem.wantedLevel
                }
                oldDebtItem.copy(wantedLevel = asInt)
            }
            6 -> oldDebtItem.copy(complexity = aValue as Complexity)
            7 -> oldDebtItem.copy(status = aValue as Status)
            8 -> oldDebtItem.copy(priority = aValue as Priority)
            9 -> oldDebtItem.copy(risk = aValue as Risk)
            10 -> oldDebtItem.copy(targetVersion = aValue as String)
            11 -> oldDebtItem.copy(comment = aValue as String)
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
            debtItem.title,
            debtItem.description,
            debtItem.username,
            debtItem.wantedLevel,
            debtItem.complexity,
            debtItem.status,
            debtItem.priority,
            debtItem.risk,
            debtItem.targetVersion,
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