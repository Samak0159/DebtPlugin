package com.github.fligneul.debtplugin.debt.toolwindow.chart.panel;

import java.util.function.Supplier;

public enum EChart {
    Pie(PieChartPanel::new),
    Bar(BarChartPanel::new);


    private final Supplier<AChartPanel> chartSupplier;
    private AChartPanel _instance;

    EChart(Supplier<AChartPanel> chartPanelSupplier) {
        this.chartSupplier = chartPanelSupplier;
    }

    public AChartPanel getChartInstance() {
        if (_instance == null) {
            _instance = chartSupplier.get();
        }

        return _instance;
    }
}
