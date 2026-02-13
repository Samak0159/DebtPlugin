package com.github.fligneul.debtplugin.debt.service;

public interface DebtServiceSelectionListener {
    void selectFile(String file);
    void selectLine(int line);
}
