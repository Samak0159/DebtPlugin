package com.github.fligneul.debtplugin.debt

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBTextField
import com.intellij.ui.layout.panel
import javax.swing.DefaultComboBoxModel
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel

class AddDebtDialog : DialogWrapper(true) {
    private val titleField = JBTextField()
    private val descriptionField = JBTextField()
    private val wantedLevelSpinner = JSpinner(SpinnerNumberModel(3, 1, 5, 1))
    private val complexityComboBox = JComboBox(DefaultComboBoxModel(Complexity.values()))
    private val statusComboBox = JComboBox(DefaultComboBoxModel(Status.values()))
    private val priorityComboBox = JComboBox(DefaultComboBoxModel(Priority.values()))
    private val riskComboBox = JComboBox(DefaultComboBoxModel(Risk.values()))
    private val targetVersionField = JBTextField()

    var titleText: String = ""
        private set
    var description: String = ""
        private set
    var wantedLevel: Int = 3
        private set
    var complexity: Complexity = Complexity.Medium
        private set
    var status: Status = Status.Submitted
        private set
    var priority: Priority = Priority.Medium
        private set
    var risk: Risk = Risk.Medium
        private set
    var targetVersion: String = ""
        private set

    init {
        title = "Add New Debt"
        init()
    }

    override fun createCenterPanel(): JComponent {
        return panel {
            row(JLabel("Title:")) { component(titleField) }
            row(JLabel("Description:")) { component(descriptionField) }
            row(JLabel("Wanted Level (1-5):")) { component(wantedLevelSpinner) }
            row(JLabel("Complexity:")) { component(complexityComboBox) }
            row(JLabel("Status:")) { component(statusComboBox) }
            row(JLabel("Priority:")) { component(priorityComboBox) }
            row(JLabel("Risk:")) { component(riskComboBox) }
            row(JLabel("Target Version:")) { component(targetVersionField) }
        }
    }

    override fun doOKAction() {
        titleText = titleField.text
        description = descriptionField.text
        wantedLevel = (wantedLevelSpinner.value as Number).toInt()
        complexity = complexityComboBox.selectedItem as Complexity
        status = statusComboBox.selectedItem as Status
        priority = priorityComboBox.selectedItem as Priority
        risk = riskComboBox.selectedItem as Risk
        targetVersion = targetVersionField.text
        super.doOKAction()
    }
}
