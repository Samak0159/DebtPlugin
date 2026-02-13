package com.github.fligneul.debtplugin.debt.toolwindow.table;

import com.github.fligneul.debtplugin.debt.model.DebtItem;
import com.github.fligneul.debtplugin.debt.service.ColumnService;
import com.github.fligneul.debtplugin.debt.service.DebtProviderService;
import com.github.fligneul.debtplugin.debt.service.DebtService;
import com.github.fligneul.debtplugin.debt.service.DebtServiceSelectionListener;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBScrollPane;

import javax.swing.JPanel;
import javax.swing.table.TableRowSorter;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.LayoutManager;
import java.util.Comparator;
import java.util.TreeSet;

public class DebtTableContainer extends JPanel {

    private final DebtTable table;
    private final DebtTableFilter filters;
    private final DebtService debtService;
    private final DebtProviderService debtProviderService;

    public DebtTableContainer(final Project project, final LayoutManager layout) {
        super(layout);
        // Column services and selector
        this.debtService = project.getService(DebtService.class);
        this.debtProviderService = project.getService(DebtProviderService.class);
        final ColumnService columnService = project.getService(ColumnService.class);

        // Filters bar at the top
        this.table = new DebtTable(project, debtService, columnService);
        final TableRowSorter<DebtTableModel> sorter = new TableRowSorter<>(table.getTableModel());
        this.filters = new DebtTableFilter(debtService, debtProviderService, table, columnService, sorter);
        table.setRowSorter(sorter);

        project.getMessageBus().connect().subscribe(DebtService.SELECTION_TOPIC, new DebtServiceSelectionListener() {
            @Override
            public void selectFile(String file) {
                filters.setFileFilterValue(table.getTableModel().displayedFile(file));
            }

            @Override
            public void selectLine(final int line) {
                filters.setLineFilter(String.valueOf(line));
            }
        });

        this.add(new JBScrollPane(table), BorderLayout.CENTER);
        this.add(new JBScrollPane(filters), BorderLayout.NORTH);

        // Bottom buttons panel (export only; refresh moved to toolwindow title bar)
        final JPanel bottomButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottomButtons.add(new DebtItemXslxExporter(project, table, debtProviderService.currentItems()));

        this.add(bottomButtons, BorderLayout.SOUTH);
    }

    public void updateTable() {
        table.updateTable();

        table.getTableModel().clearAll();

        final TreeSet<Integer> wantedLevels = new TreeSet<>(Comparator.naturalOrder());
        final TreeSet<Integer> estimations = new TreeSet<>(Comparator.naturalOrder());

        for (DebtItem item : debtProviderService.currentItems()) {
            table.getTableModel().addDebtItem(item);
            wantedLevels.add(item.getWantedLevel());
            estimations.add(item.getEstimation());
        }

        filters.updateFilters(wantedLevels, estimations);
    }
}
