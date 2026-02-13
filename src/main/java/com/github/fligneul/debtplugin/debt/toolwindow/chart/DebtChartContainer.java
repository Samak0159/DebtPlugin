package com.github.fligneul.debtplugin.debt.toolwindow.chart;

import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBScrollPane;

import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.LayoutManager;

public class DebtChartContainer extends JPanel {

    private final DebtChartFilter filter;

    public DebtChartContainer(final Project project, final LayoutManager layout) {
        super(layout);

        final ModulePieChartPanel pieChartPanel = new ModulePieChartPanel();
        filter = new DebtChartFilter(project, pieChartPanel);

        this.add(new JBScrollPane(filter), BorderLayout.NORTH);

        this.add(new JBScrollPane(pieChartPanel), BorderLayout.CENTER);
    }

    public void updateChart() {
        filter.updateFilters();
    }
}
