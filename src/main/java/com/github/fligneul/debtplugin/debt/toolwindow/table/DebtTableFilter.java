package com.github.fligneul.debtplugin.debt.toolwindow.table;

import com.github.fligneul.debtplugin.debt.model.Complexity;
import com.github.fligneul.debtplugin.debt.model.Risk;
import com.github.fligneul.debtplugin.debt.model.Status;
import com.github.fligneul.debtplugin.debt.service.ColumnService;
import com.github.fligneul.debtplugin.debt.service.DebtProviderService;
import com.github.fligneul.debtplugin.debt.service.DebtService;
import com.github.fligneul.debtplugin.debt.toolwindow.MultiSelectFilter;
import com.intellij.openapi.diagnostic.Logger;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.RowFilter;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.TableRowSorter;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class DebtTableFilter extends JPanel {
    private static final Logger LOG = Logger.getInstance(DebtTableContainer.class);

    private final DebtService debtService;
    private final DebtProviderService debtProviderService;
    private final JPanel row2Panel;
    private final JPanel row3Panel;
    private final TableRowSorter<DebtTableModel> sorter;

    private final JButton toggleFiltersButton = new JButton("-"); // Expanded state shows "-"
    private final MultiSelectFilter<Integer> columnSelector = new MultiSelectFilter<>("Columns");
    private final JTextField fileFilter = new JTextField(8);
    private final JTextField lineFilter = new JTextField(5);
    private final JTextField titleFilter = new JTextField(8);
    private final JTextField descFilter = new JTextField(8);
    private final JTextField userFilter = new JTextField(6);
    private final MultiSelectFilter<Integer> wantedLevelFilter = new MultiSelectFilter<>("WantedLevel");
    private final MultiSelectFilter<Complexity> complexityFilter = new MultiSelectFilter<>("Complexity");
    private final MultiSelectFilter<Status> statusFilter = new MultiSelectFilter<>("Status");
    private final MultiSelectFilter<String> priorityFilter = new MultiSelectFilter<>("Priority");
    private final MultiSelectFilter<Risk> riskFilter = new MultiSelectFilter<>("Risk");
    private final JTextField targetVersionFilter = new JTextField(6);
    private final JTextField commentFilter = new JTextField(8);
    private final MultiSelectFilter<Integer> estimationFilter = new MultiSelectFilter<>("Estimation");
    private final MultiSelectFilter<String> moduleFilter = new MultiSelectFilter<>("Module");
    private final JTextField jiraFilter = new JTextField(8);
    private final DebtTable table;
    private final ColumnService columnService;

    private boolean filtersCollapsed = false;

    public DebtTableFilter(final DebtService debtService, final DebtProviderService debtProviderService, final DebtTable table, final ColumnService columnService, final TableRowSorter<DebtTableModel> sorter) {
        this.debtService = debtService;
        this.debtProviderService = debtProviderService;
        this.sorter = sorter;
        this.setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        this.table = table;
        this.columnService = columnService;

        row2Panel = generateRow2();
        row3Panel = generateRow3();

        this.add(generateRow1());
        this.add(row2Panel);
        this.add(row3Panel);

        initFilters();
    }

    private JPanel generateRow1() {
        JPanel rowPanel = new JPanel(new BorderLayout(8, 2));
        rowPanel.add(new JLabel("Filter :"), BorderLayout.WEST);

        // Column selector in the center of the top rowPanel
        // Options are model indices
        List<Integer> colOptions = new ArrayList<>();
        for (int i = 0; i < table.getTableModel().getColumnCount(); i++) colOptions.add(i);
        columnSelector.setOptions(colOptions);
        columnSelector.setRenderer((Integer idx) -> {
            String name = table.getTableModel().getColumnName(idx);
            if (name == null || name.isBlank()) return ColumnService.ACTIONS_NAME;
            return name;
        });
        // Initialize selection reflects current visibility (if all visible -> show All)
        List<Integer> visible = columnService.getVisibleModelIndices();
        if (visible.size() == table.getTableModel().getColumnCount()) {
            columnSelector.clearSelection();
        } else {
            columnSelector.setSelected(visible);
        }
        columnSelector.addSelectionListener(this::applyColumnSelection);
        rowPanel.add(columnSelector);

        toggleFiltersButton.setText(filtersCollapsed ? "+" : "-");
        toggleFiltersButton.addActionListener(e -> {
            filtersCollapsed = !filtersCollapsed;
            if (row2Panel != null) row2Panel.setVisible(!filtersCollapsed);
            if (row3Panel != null) row3Panel.setVisible(!filtersCollapsed);
            toggleFiltersButton.setText(filtersCollapsed ? "+" : "-");
            this.revalidate();
            this.repaint();
        });
        rowPanel.add(toggleFiltersButton, BorderLayout.EAST);

        return rowPanel;
    }

    private JPanel generateRow2() {
        var rowPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        rowPanel.add(new JLabel("File:"));
        rowPanel.add(fileFilter);
        rowPanel.add(new JLabel("Line:"));
        rowPanel.add(lineFilter);
        rowPanel.add(new JLabel("Title:"));
        rowPanel.add(titleFilter);
        rowPanel.add(new JLabel("Description:"));
        rowPanel.add(descFilter);
        rowPanel.add(new JLabel("User:"));
        rowPanel.add(userFilter);
        rowPanel.add(new JLabel("TargetVersion:"));
        rowPanel.add(targetVersionFilter);
        rowPanel.add(new JLabel("Comment:"));
        rowPanel.add(commentFilter);
        rowPanel.add(new JLabel("Jira:"));
        rowPanel.add(jiraFilter);
        rowPanel.setVisible(!filtersCollapsed);

        return rowPanel;
    }

    private JPanel generateRow3() {
        var rowPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        rowPanel.add(new JLabel("WantedLevel:"));
        rowPanel.add(wantedLevelFilter);
        rowPanel.add(new JLabel("Complexity:"));
        rowPanel.add(complexityFilter);
        rowPanel.add(new JLabel("Status:"));
        rowPanel.add(statusFilter);
        rowPanel.add(new JLabel("Priority:"));
        rowPanel.add(priorityFilter);
        rowPanel.add(new JLabel("Risk:"));
        rowPanel.add(riskFilter);
        rowPanel.add(new JLabel("Estimation:"));
        rowPanel.add(estimationFilter);
        rowPanel.add(new JLabel("Module:"));
        rowPanel.add(moduleFilter);
        rowPanel.setVisible(!filtersCollapsed);

        return rowPanel;
    }

    private void initFilters() {
        // Wire filter listeners
        DocumentListener docListener = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                applyFilters();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                applyFilters();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                applyFilters();
            }
        };
        fileFilter.getDocument().addDocumentListener(docListener);
        lineFilter.getDocument().addDocumentListener(docListener);
        titleFilter.getDocument().addDocumentListener(docListener);
        descFilter.getDocument().addDocumentListener(docListener);
        userFilter.getDocument().addDocumentListener(docListener);
        targetVersionFilter.getDocument().addDocumentListener(docListener);
        commentFilter.getDocument().addDocumentListener(docListener);
        jiraFilter.getDocument().addDocumentListener(docListener);

        // Multi-select filters notify via selection listeners
        wantedLevelFilter.addSelectionListener(this::applyFilters);
        complexityFilter.addSelectionListener(this::applyFilters);
        statusFilter.addSelectionListener(this::applyFilters);
        priorityFilter.addSelectionListener(this::applyFilters);
        riskFilter.addSelectionListener(this::applyFilters);
        estimationFilter.addSelectionListener(this::applyFilters);
        moduleFilter.addSelectionListener(this::applyFilters);

        // Configure multi-select enum filters
        complexityFilter.setOptions(Arrays.asList(Complexity.values()));
        statusFilter.setOptions(Arrays.asList(Status.values()));
        riskFilter.setOptions(Arrays.asList(Risk.values()));
    }

    private void applyFilters() {
        List<RowFilter<DebtTableModel, Object>> filters = new ArrayList<>();

        addTextFilter(filters, fileFilter.getText(), 0);
        addTextExactFilter(filters, lineFilter.getText(), 1);
        addTextFilter(filters, titleFilter.getText(), 2);
        addTextFilter(filters, descFilter.getText(), 3);
        addTextFilter(filters, userFilter.getText(), 4);

        addMultiSelectExact(filters, wantedLevelFilter.getSelected(), 5);
        addMultiSelectExact(filters, complexityFilter.getSelected(), 6);
        addMultiSelectExact(filters, statusFilter.getSelected(), 7);
        addMultiSelectExact(filters, priorityFilter.getSelected(), 8);
        addMultiSelectExact(filters, riskFilter.getSelected(), 9);

        addTextFilter(filters, targetVersionFilter.getText(), 10);
        addTextFilter(filters, commentFilter.getText(), 11);
        addMultiSelectExact(filters, estimationFilter.getSelected(), 12);
        addTextFilter(filters, jiraFilter.getText(), 13);

        final Set<String> modulesSelected = moduleFilter.getSelected()
                .stream()
                .map(value -> "Unknown".equals(value)
                        ? ""
                        : value)
                .collect(Collectors.toSet());
        addMultiSelectExact(filters, modulesSelected, 14);

        if (filters.isEmpty()) {
            sorter.setRowFilter(null);
        } else {
            sorter.setRowFilter(RowFilter.andFilter(filters));
        }
        if (LOG.isDebugEnabled()) {
            List<String> actives = new ArrayList<>();
            if (!fileFilter.getText().isBlank()) actives.add("file");
            if (!titleFilter.getText().isBlank()) actives.add("title");
            if (!descFilter.getText().isBlank()) actives.add("description");
            if (!userFilter.getText().isBlank()) actives.add("user");
            if (!wantedLevelFilter.getSelected().isEmpty()) actives.add("wantedLevel");
            if (!complexityFilter.getSelected().isEmpty()) actives.add("complexity");
            if (!statusFilter.getSelected().isEmpty()) actives.add("status");
            if (!priorityFilter.getSelected().isEmpty()) actives.add("priority");
            if (!riskFilter.getSelected().isEmpty()) actives.add("risk");
            if (!targetVersionFilter.getText().isBlank()) actives.add("targetVersion");
            if (!commentFilter.getText().isBlank()) actives.add("comment");
            if (!estimationFilter.getSelected().isEmpty()) actives.add("estimation");
            if (!moduleFilter.getSelected().isEmpty()) actives.add("module");
            if (!jiraFilter.getText().isBlank()) actives.add("Jira");
            LOG.debug("Filters applied. active=" + actives + " visibleRows=" + table.getRowCount());
        }
    }

    private void addTextFilter(List<RowFilter<DebtTableModel, Object>> filters, String text, int column) {
        if (text != null && !text.isBlank()) {
            String expr = "(?i)" + Pattern.quote(text.trim());
            filters.add(RowFilter.regexFilter(expr, column));
        }
    }

    private void addTextExactFilter(final List<RowFilter<DebtTableModel, Object>> filters, final String text, final int column) {
        if (text != null && !text.isBlank()) {
            filters.add(RowFilter.numberFilter(RowFilter.ComparisonType.EQUAL, Integer.valueOf(text.trim()), column));
        }
    }

    private <T> void addMultiSelectExact(List<RowFilter<DebtTableModel, Object>> filters, Set<T> selected, int column) {
        if (selected == null || selected.isEmpty()) return; // no filter (All)
        List<RowFilter<DebtTableModel, Object>> ors = new ArrayList<>();
        for (T val : selected) {
            if (val == null) continue;
            String expr = "^" + Pattern.quote(String.valueOf(val)) + "$";
            ors.add(RowFilter.regexFilter(expr, column));
        }
        if (!ors.isEmpty()) {
            filters.add(RowFilter.orFilter(ors));
        }
    }

    private void applyColumnSelection() {
        try {
            Set<Integer> selected = columnSelector.getSelected();
            // Update service visibility and apply to the table
            columnService.setVisibleByModelIndexSelection(selected);
            columnService.applyTo(table);
        } catch (Exception ex) {
            if (LOG.isDebugEnabled()) LOG.debug("applyColumnSelection failed: " + ex.getMessage(), ex);
        }
    }

    public void setFileFilterValue(final String file) {
        fileFilter.setText(file);
    }

    public void setLineFilter(final String line) {
        lineFilter.setText(line);
    }

    public void updateFilters(final TreeSet<String> priorities, final TreeSet<Integer> wantedLevels, final TreeSet<Integer> estimations) {
        final LinkedHashMap<String, Integer> modules = debtService.extractModules(debtProviderService.currentItems());

        moduleFilter.setOptions(modules.keySet());

        priorityFilter.setOptions(priorities);
        wantedLevelFilter.setOptions(wantedLevels);
        estimationFilter.setOptions(estimations);

        // Sync selector selection to currently visible columns
        List<Integer> visible = columnService.getVisibleModelIndices();
        if (visible.size() == table.getTableModel().getColumnCount()) {
            columnSelector.clearSelection();
        } else {
            columnSelector.setSelected(visible);
        }


        // Apply filters to table and update chart aggregation
        applyFilters();
    }
}
