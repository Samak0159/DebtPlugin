package com.github.fligneul.debtplugin.debt.toolwindow.chart.panel;

import com.github.fligneul.debtplugin.debt.settings.DebtSettings;

import java.util.function.Function;

public enum EChart {
    Pie(debtSettings -> new PieChartPanel(debtSettings.getState().getChartClassifier())),
    Bar(debtSettings -> new BarChartPanel(debtSettings.getState().getChartClassifier()));


    private final Function<DebtSettings, AChartPanel> chartFactory;
    private AChartPanel _instance;

    EChart(Function<DebtSettings, AChartPanel> chartPanelSupplier) {
        this.chartFactory = chartPanelSupplier;
    }

    public AChartPanel getChartInstance(DebtSettings debtSettings) {
        if (_instance == null) {
            _instance = chartFactory.apply(debtSettings);
        }

        return _instance;
    }
}
