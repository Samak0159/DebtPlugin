package com.github.fligneul.debtplugin.debt.toolwindow.chart;

import com.github.fligneul.debtplugin.debt.model.Complexity;
import com.github.fligneul.debtplugin.debt.model.DebtItem;
import com.github.fligneul.debtplugin.debt.model.Risk;
import com.github.fligneul.debtplugin.debt.model.Status;
import com.github.fligneul.debtplugin.debt.service.DebtProviderService;
import com.github.fligneul.debtplugin.debt.service.DebtService;
import com.github.fligneul.debtplugin.debt.toolwindow.MultiSelectFilter;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Stream;

public class DebtChartFilter extends JPanel {
    private final ModulePieChartPanel pieChartPanel;
    private final DebtService debtService;
    private final DebtProviderService debtProviderService;

    private final JButton toggleFiltersButtonChart = new JButton("-");
    private final JTextField fileFilterChart = new JTextField(8);
    private final JTextField titleFilterChart = new JTextField(8);
    private final JTextField descFilterChart = new JTextField(8);
    private final JTextField userFilterChart = new JTextField(6);
    private final MultiSelectFilter<Integer> wantedLevelFilterChart = new MultiSelectFilter<>("WantedLevel");
    private final MultiSelectFilter<Complexity> complexityFilterChart = new MultiSelectFilter<>("Complexity");
    private final MultiSelectFilter<Status> statusFilterChart = new MultiSelectFilter<>("Status");
    private final MultiSelectFilter<String> priorityFilterChart = new MultiSelectFilter<>("Priority");
    private final MultiSelectFilter<Risk> riskFilterChart = new MultiSelectFilter<>("Risk");
    private final JTextField targetVersionFilterChart = new JTextField(6);
    private final JTextField commentFilterChart = new JTextField(8);
    private final MultiSelectFilter<Integer> estimationFilterChart = new MultiSelectFilter<>("Estimation");
    private final MultiSelectFilter<String> moduleFilterChart = new MultiSelectFilter<>("Module");
    private final JTextField jiraFilterChart = new JTextField(8);

    private boolean filtersCollapsedChart = false;

    public DebtChartFilter(final Project project, final ModulePieChartPanel pieChartPanel) {
        this.pieChartPanel = pieChartPanel;
        this.debtService = project.getService(DebtService.class);
        this.debtProviderService = project.getService(DebtProviderService.class);

        // Configure chart tab enum filters as well
        complexityFilterChart.setOptions(Arrays.asList(Complexity.values()));
        statusFilterChart.setOptions(Arrays.asList(Status.values()));
        riskFilterChart.setOptions(Arrays.asList(Risk.values()));

        this.setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

        final JPanel row2 = generateRow2();
        final JPanel row3 = generateRow3();
        final JPanel row4 = generateRow4();
        final JPanel row1Chart = generateRow1(row2, row3, row4);

        // Initial visibility
        toggleFiltersButtonChart.setText(filtersCollapsedChart ? "+" : "-");

        this.add(row1Chart);
        this.add(row2);
        this.add(row3);
        this.add(row4);

        initFilters();
    }

    private JPanel generateRow1(final JPanel... rows) {
        JPanel row = new JPanel(new BorderLayout(8, 2));
        row.add(new JLabel("Filter :"), BorderLayout.WEST);
        toggleFiltersButtonChart.addActionListener(e -> {
            filtersCollapsedChart = !filtersCollapsedChart;
            Stream.of(rows).forEach(subRow -> subRow.setVisible(!filtersCollapsedChart));
            toggleFiltersButtonChart.setText(filtersCollapsedChart ? "+" : "-");
            this.revalidate();
            this.repaint();
        });

        final JButton clearButton = new JButton();
        clearButton.setIcon(AllIcons.Actions.DeleteTag);
        clearButton.setToolTipText("Clear Filters");
        clearButton.addActionListener(e -> clearFilters());

        final JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        rightPanel.add(clearButton);
        rightPanel.add(toggleFiltersButtonChart);

        row.add(rightPanel, BorderLayout.EAST);
        return row;
    }

    private void clearFilters() {
        fileFilterChart.setText("");
        titleFilterChart.setText("");
        descFilterChart.setText("");
        userFilterChart.setText("");
        wantedLevelFilterChart.clearSelection();
        complexityFilterChart.clearSelection();
        statusFilterChart.clearSelection();
        priorityFilterChart.clearSelection();
        riskFilterChart.clearSelection();
        targetVersionFilterChart.setText("");
        commentFilterChart.setText("");
        estimationFilterChart.clearSelection();
        moduleFilterChart.clearSelection();
        jiraFilterChart.setText("");
    }

    private JPanel generateRow2() {
        final JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        row.add(new JLabel("File:"));
        row.add(fileFilterChart);
        row.add(new JLabel("Title:"));
        row.add(titleFilterChart);
        row.add(new JLabel("Description:"));
        row.add(descFilterChart);
        row.add(new JLabel("User:"));
        row.add(userFilterChart);
        row.add(new JLabel("TargetVersion:"));
        row.add(targetVersionFilterChart);
        row.add(new JLabel("Comment:"));
        row.add(commentFilterChart);
        row.add(new JLabel("Jira:"));
        row.add(jiraFilterChart);

        row.setVisible(!filtersCollapsedChart);

        return row;
    }

    private JPanel generateRow3() {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        row.add(new JLabel("WantedLevel:"));
        row.add(wantedLevelFilterChart);
        row.add(new JLabel("Complexity:"));
        row.add(complexityFilterChart);
        row.add(new JLabel("Status:"));
        row.add(statusFilterChart);
        row.add(new JLabel("Priority:"));
        row.add(priorityFilterChart);
        row.add(new JLabel("Risk:"));
        row.add(riskFilterChart);
        row.add(new JLabel("Estimation:"));
        row.add(estimationFilterChart);
        row.add(new JLabel("Module:"));
        row.add(moduleFilterChart);

        row.setVisible(!filtersCollapsedChart);

        return row;
    }

    private JPanel generateRow4() {
        final ComboBox<EClissifiers> groupByComboBox = new ComboBox<>(EClissifiers.values());
        groupByComboBox.setSelectedItem(EClissifiers.DEFAULT);
        groupByComboBox.addActionListener(e -> {
            pieChartPanel.setGroupBy((EClissifiers) groupByComboBox.getSelectedItem());
            this.updateFilters();
        });

        final JPanel groupByPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        groupByPanel.add(new JLabel("Group by:"));
        groupByPanel.add(groupByComboBox);

        return groupByPanel;
    }

    private void initFilters() {
        // Wire chart filter listeners
        DocumentListener docListenerChart = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                filterValues();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                filterValues();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                filterValues();
            }
        };
        fileFilterChart.getDocument().addDocumentListener(docListenerChart);
        titleFilterChart.getDocument().addDocumentListener(docListenerChart);
        descFilterChart.getDocument().addDocumentListener(docListenerChart);
        userFilterChart.getDocument().addDocumentListener(docListenerChart);
        targetVersionFilterChart.getDocument().addDocumentListener(docListenerChart);
        commentFilterChart.getDocument().addDocumentListener(docListenerChart);
        jiraFilterChart.getDocument().addDocumentListener(docListenerChart);

        wantedLevelFilterChart.addSelectionListener(this::filterValues);
        complexityFilterChart.addSelectionListener(this::filterValues);
        statusFilterChart.addSelectionListener(this::filterValues);
        priorityFilterChart.addSelectionListener(this::filterValues);
        riskFilterChart.addSelectionListener(this::filterValues);
        estimationFilterChart.addSelectionListener(this::filterValues);
        moduleFilterChart.addSelectionListener(this::filterValues);
    }

    public void updateFilters() {
        final TreeSet<String> priorities = new TreeSet<>(Comparator.naturalOrder());
        final TreeSet<Integer> wantedLevels = new TreeSet<>(Comparator.naturalOrder());
        final TreeSet<Integer> estimations = new TreeSet<>(Comparator.naturalOrder());

        for (DebtItem item : debtProviderService.currentItems()) {
            priorities.add(item.getPriority());
            wantedLevels.add(item.getWantedLevel());
            estimations.add(item.getEstimation());
        }

        // Aggregate modules from items that match the chart-specific filters
        priorityFilterChart.setOptions(priorities);
        wantedLevelFilterChart.setOptions(wantedLevels);
        estimationFilterChart.setOptions(estimations);

        moduleFilterChart.setOptions(debtService.extractModules(debtProviderService.currentItems()).keySet());

        filterValues();
    }

    private void filterValues() {
        final List<DebtItem> items = debtProviderService.currentItems()
                .stream()
                .filter(debtItem -> chartFilterContaining(debtItem, DebtItem::getFile, fileFilterChart))
                .filter(debtItem -> chartFilterContaining(debtItem, DebtItem::getTitle, titleFilterChart))
                .filter(debtItem -> chartFilterContaining(debtItem, DebtItem::getDescription, descFilterChart))
                .filter(debtItem -> chartFilterContaining(debtItem, DebtItem::getUsername, userFilterChart))
                .filter(debtItem -> chartFilterContaining(debtItem, DebtItem::getWantedLevel, wantedLevelFilterChart))
                .filter(debtItem -> chartFilterContaining(debtItem, DebtItem::getComplexity, complexityFilterChart))
                .filter(debtItem -> chartFilterContaining(debtItem, DebtItem::getStatus, statusFilterChart))
                .filter(debtItem -> chartFilterContaining(debtItem, DebtItem::getPriority, priorityFilterChart))
                .filter(debtItem -> chartFilterContaining(debtItem, DebtItem::getRisk, riskFilterChart))
                .filter(debtItem -> chartFilterContaining(debtItem, DebtItem::getTargetVersion, targetVersionFilterChart))
                .filter(debtItem -> chartFilterContaining(debtItem, DebtItem::getComment, commentFilterChart))
                .filter(debtItem -> chartFilterContaining(debtItem, DebtItem::getEstimation, estimationFilterChart))
                .filter(debtItem -> {
                    return chartFilterContaining(debtItem, currentItem -> {
                        return "".equals(currentItem.getCurrentModule())
                                ? "Unknown"
                                : currentItem.getCurrentModule();
                    }, moduleFilterChart);
                })
                .filter(debtItem -> chartFilterContaining(debtItem, DebtItem::getJira, jiraFilterChart))
                .toList();

        pieChartPanel.setData(items);
    }


    private <T> boolean chartFilterContaining(final DebtItem debtItem, final Function<DebtItem, T> getterFct, final MultiSelectFilter<T> filter) {
        return filter.getSelected().isEmpty()
                ? true
                : filter.getSelected()
                  .stream()
                  .anyMatch(selected -> getterFct.apply(debtItem).equals(selected));
    }

    private boolean chartFilterContaining(final DebtItem debtItem, final Function<DebtItem, String> getterFct, final JTextField filter) {
        return getterFct.apply(debtItem).contains(filter.getText());
    }

}
