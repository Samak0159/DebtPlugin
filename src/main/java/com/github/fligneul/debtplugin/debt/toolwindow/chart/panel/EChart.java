package com.github.fligneul.debtplugin.debt.toolwindow.chart.panel;

import com.github.fligneul.debtplugin.debt.settings.DebtSettings;
import com.intellij.openapi.project.Project;

import java.util.function.BiFunction;

public enum EChart {
    Pie((debtSettings, project) -> new PieChartPanel(debtSettings.getState().getChartClassifier(), project)),
    Bar((debtSettings, project) -> new BarChartPanel(debtSettings.getState().getChartClassifier(), project));


    private final BiFunction<DebtSettings, Project, AChartPanel> chartFactory;
    private AChartPanel _instance;

    EChart(BiFunction<DebtSettings, Project, AChartPanel> chartPanelSupplier) {
        this.chartFactory = chartPanelSupplier;
    }

    public AChartPanel getChartInstance(DebtSettings debtSettings, Project project) {
        if (_instance == null) {
            _instance = chartFactory.apply(debtSettings, project);
        }

        return _instance;
    }
}
