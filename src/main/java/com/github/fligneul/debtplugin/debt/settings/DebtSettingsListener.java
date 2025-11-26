package com.github.fligneul.debtplugin.debt.settings;

public interface DebtSettingsListener {
    void settingsChanged(DebtSettings.State settings);
}
