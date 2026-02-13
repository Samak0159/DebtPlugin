package com.github.fligneul.debtplugin.debt.toolwindow;

import com.github.fligneul.debtplugin.debt.service.DebtService;
import com.github.fligneul.debtplugin.debt.service.DebtServiceListener;
import com.github.fligneul.debtplugin.debt.settings.DebtSettings;
import com.github.fligneul.debtplugin.debt.settings.DebtSettingsListener;
import com.github.fligneul.debtplugin.debt.toolwindow.chart.DebtChartContainer;
import com.github.fligneul.debtplugin.debt.toolwindow.relationship.RelationshipGraphPanel;
import com.github.fligneul.debtplugin.debt.toolwindow.table.DebtTableContainer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBScrollPane;

import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import java.awt.BorderLayout;

public class DebtToolWindow extends JPanel {
    private static final Logger LOG = Logger.getInstance(DebtToolWindow.class);
    private final Project project;

    private DebtTableContainer debtTableContainer;
    private DebtChartContainer debtChartContainer;
    private RelationshipGraphPanel relationshipGraphPanel;

    public DebtToolWindow(Project project) {
        this.project = project;

        createContent();

        // Refresh the table automatically when settings change (e.g., username updated)
        project.getMessageBus().connect().subscribe(DebtSettings.TOPIC, new DebtSettingsListener() {
            @Override
            public void settingsChanged(DebtSettings.State settings) {
                LOG.info("Settings changed: username=" + settings.getUsername() + " relDebtPath=" + settings.getDebtFilePath(project));
                // Apply column visibility from settings
                // Refresh data
                update();
            }
        });

        project.getMessageBus().connect().subscribe(DebtService.TOPIC, new DebtServiceListener() {
            @Override
            public void refresh() {
                if (LOG.isDebugEnabled()) LOG.debug("Refresh requested from toolwindow");
                update();
            }
        });
    }

    private void createContent() {
        // Build the existing list UI into its own panel
        debtTableContainer = new DebtTableContainer(project, new BorderLayout());

        // Build the chart tab with its own filters
        debtChartContainer = new DebtChartContainer(project, new BorderLayout());
        relationshipGraphPanel = new RelationshipGraphPanel(project);

        // Root with tabs
        final JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("debts", debtTableContainer);
        tabs.addTab("modules", debtChartContainer);
        tabs.addTab("RelationShip", new JBScrollPane(relationshipGraphPanel));

        this.setLayout(new BorderLayout());
        this.add(tabs, BorderLayout.CENTER);

        update();
    }

    private void update() {
        debtTableContainer.updateTable();
        debtChartContainer.updateChart();
        relationshipGraphPanel.update();
    }

}
