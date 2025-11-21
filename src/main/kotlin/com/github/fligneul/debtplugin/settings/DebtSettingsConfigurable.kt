package com.github.fligneul.debtplugin.settings

import com.github.fligneul.debtplugin.debt.DebtService
import com.intellij.openapi.components.service
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent

class DebtSettingsConfigurable(private val project: Project) : Configurable {

    private val settings = project.service<DebtSettings>()
    private val debtFilePathField = JBTextField()
    private val usernameField = JBTextField()

    override fun getDisplayName(): String {
        return "Debt Plugin"
    }

    override fun createComponent(): JComponent {
        // Initialize fields from settings
        debtFilePathField.text = settings.state.debtFilePath
        usernameField.text = settings.getOrInitUsername()
        return FormBuilder.createFormBuilder()
            .addLabeledComponent("Debt file path:", debtFilePathField)
            .addLabeledComponent("Username:", usernameField)
            .panel
    }

    override fun isModified(): Boolean {
        return debtFilePathField.text != settings.state.debtFilePath ||
                usernameField.text != settings.state.username
    }

    override fun apply() {
        val oldUsername = settings.state.username
        val newUsername = usernameField.text.trim()

        // Persist debt file path change
        settings.state.debtFilePath = debtFilePathField.text

        // If username is non-blank and changed, migrate existing debts and update settings
        if (newUsername.isNotBlank() && newUsername != oldUsername) {
            val debtService = project.service<DebtService>()
            debtService.migrateUsername(oldUsername, newUsername)
            settings.state.username = newUsername
        }

        // Notify listeners
        project.messageBus.syncPublisher(DebtSettings.TOPIC).settingsChanged(settings.state)
    }

    override fun reset() {
        debtFilePathField.text = settings.state.debtFilePath
        usernameField.text = settings.getOrInitUsername()
    }
}
