package com.github.fligneul.debtplugin.debt.service;

public interface DebtServiceListener {
    void refresh();

    void selectFile(String file);
    void selectLine(int line);
}
