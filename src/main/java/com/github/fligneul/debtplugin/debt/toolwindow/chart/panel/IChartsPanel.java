package com.github.fligneul.debtplugin.debt.toolwindow.chart.panel;

import com.github.fligneul.debtplugin.debt.model.DebtItem;
import com.github.fligneul.debtplugin.debt.toolwindow.chart.EClassifiers;

import java.util.List;

public interface IChartsPanel {
    void setGroupBy(EClassifiers groupByField);

    void setData(List<DebtItem> items, final int limit);
}
