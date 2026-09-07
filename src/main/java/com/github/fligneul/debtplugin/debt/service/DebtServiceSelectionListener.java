package com.github.fligneul.debtplugin.debt.service;

import com.github.fligneul.debtplugin.debt.toolwindow.chart.EClassifiers;

public interface DebtServiceSelectionListener {
    void select(String file, int line);
    void select(EClassifiers classifier, String categoryValue);
}
