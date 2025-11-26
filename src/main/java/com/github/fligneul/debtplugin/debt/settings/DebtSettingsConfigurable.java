package com.github.fligneul.debtplugin.debt.settings;

import com.github.fligneul.debtplugin.debt.service.DebtService;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class DebtSettingsConfigurable implements Configurable {
    private final Project project;
    private final DebtSettings settings;
    private final JBTextField debtFilePathField = new JBTextField();
    private final JBTextField usernameField = new JBTextField();

    public DebtSettingsConfigurable(Project project) {
        this.project = project;
        this.settings = project.getService(DebtSettings.class);
    }

    @Override
    public @Nls(capitalization = Nls.Capitalization.Title) String getDisplayName() {
        return "Debt Plugin";
    }

    @Override
    public @Nullable JComponent createComponent() {
        debtFilePathField.setText(settings.getState().getDebtFilePath());
        usernameField.setText(settings.getOrInitUsername());
        return FormBuilder.createFormBuilder()
                .addLabeledComponent("Debt file path:", debtFilePathField)
                .addLabeledComponent("Username:", usernameField)
                .getPanel();
    }

    @Override
    public boolean isModified() {
        return !debtFilePathField.getText().equals(settings.getState().getDebtFilePath()) ||
                !usernameField.getText().equals(settings.getState().getUsername());
    }

    @Override
    public void apply() {
        String oldUsername = settings.getState().getUsername();
        String newUsername = usernameField.getText().trim();

        settings.getState().setDebtFilePath(debtFilePathField.getText());

        if (!newUsername.isBlank() && !newUsername.equals(oldUsername)) {
            DebtService debtService = project.getService(DebtService.class);
            debtService.migrateUsername(oldUsername, newUsername);
            settings.getState().setUsername(newUsername);
        }

        project.getMessageBus().syncPublisher(DebtSettings.TOPIC).settingsChanged(settings.getState());
    }

    @Override
    public void reset() {
        debtFilePathField.setText(settings.getState().getDebtFilePath());
        usernameField.setText(settings.getOrInitUsername());
    }
}
