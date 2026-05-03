package com.github.fligneul.debtplugin.debt.toolwindow.chart;

import com.github.fligneul.debtplugin.debt.settings.DebtSettings;
import com.github.fligneul.debtplugin.debt.toolwindow.chart.panel.AChartPanel;
import com.github.fligneul.debtplugin.debt.toolwindow.chart.panel.EChart;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBScrollPane;

import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.LayoutManager;
import java.util.stream.Stream;

public class DebtChartContainer extends JPanel {

    private final DebtChartFilter filter;
    private final JPanel chartCards;
    private final CardLayout cardLayout;

    public DebtChartContainer(final Project project, final LayoutManager layout) {
        super(layout);
        final DebtSettings debtSettings = project.getService(DebtSettings.class);

        cardLayout = new CardLayout();
        chartCards = new JPanel(cardLayout);
        Stream.of(EChart.values())
                .forEach(chart -> {
                    final AChartPanel chartPanel = chart.getChartInstance(debtSettings);

                    chartCards.add(chartPanel, chart.name());
                });

        filter = new DebtChartFilter(project, this::showChart);

        this.add(new JBScrollPane(filter), BorderLayout.NORTH);

        this.add(new JBScrollPane(chartCards), BorderLayout.CENTER);

        showChart(debtSettings.getState().getChartType());
    }

    private void showChart(EChart chart) {
        cardLayout.show(chartCards, chart.name());
        updateChart();
    }

    public void updateChart() {
        filter.updateFilters();
    }
}
