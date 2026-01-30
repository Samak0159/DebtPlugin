package com.github.fligneul.debtplugin.debt.toolwindow;

import com.github.fligneul.debtplugin.debt.action.AddDebtDialog;
import com.github.fligneul.debtplugin.debt.model.Complexity;
import com.github.fligneul.debtplugin.debt.model.DebtItem;
import com.github.fligneul.debtplugin.debt.model.Priority;
import com.github.fligneul.debtplugin.debt.model.Repository;
import com.github.fligneul.debtplugin.debt.model.Risk;
import com.github.fligneul.debtplugin.debt.model.Status;
import com.github.fligneul.debtplugin.debt.service.DebtService;
import com.github.fligneul.debtplugin.debt.service.DebtServiceListener;
import com.github.fligneul.debtplugin.debt.settings.DebtSettings;
import com.github.fligneul.debtplugin.debt.settings.DebtSettingsListener;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import javax.swing.BoxLayout;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.RowFilter;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.TableColumnModelEvent;
import javax.swing.event.TableColumnModelListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableRowSorter;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

public class DebtToolWindow {
    private static final Logger LOG = Logger.getInstance(DebtToolWindow.class);
    private final Project project;
    private final DebtService debtService;
    private final DebtProviderService debtProviderService;
    private final DebtTableModel tableModel;
    private final JBTable table;
    private final TableRowSorter<DebtTableModel> sorter;
    private final ModulePieChartPanel pieChartPanel;
    private final RelationshipGraphPanel relationshipGraphPanel;
    // Base row height to use as minimum when auto-expanding rows
    private final int defaultRowHeight;

    // Cached items for reuse (table and chart)
    private final List<DebtItem> allItems = new ArrayList<>();

    // Column services and selector
    private final ColumnService columnService;
    private final MultiSelectFilter<Integer> columnSelector = new MultiSelectFilter<>("Columns");

    // Filter inputs (table tab)
    private final JTextField fileFilter = new JTextField(8);
    private final JTextField titleFilter = new JTextField(8);
    private final JTextField descFilter = new JTextField(8);
    private final JTextField userFilter = new JTextField(6);
    private final MultiSelectFilter<Integer> wantedLevelFilter = new MultiSelectFilter<>("WantedLevel");
    private final MultiSelectFilter<Complexity> complexityFilter = new MultiSelectFilter<>("Complexity");
    private final MultiSelectFilter<Status> statusFilter = new MultiSelectFilter<>("Status");
    private final MultiSelectFilter<Priority> priorityFilter = new MultiSelectFilter<>("Priority");
    private final MultiSelectFilter<Risk> riskFilter = new MultiSelectFilter<>("Risk");
    private final JTextField targetVersionFilter = new JTextField(6);
    private final JTextField commentFilter = new JTextField(8);

    // Filter inputs (chart tab)
    private final JTextField fileFilterChart = new JTextField(8);
    private final JTextField titleFilterChart = new JTextField(8);
    private final JTextField descFilterChart = new JTextField(8);
    private final JTextField userFilterChart = new JTextField(6);
    private final MultiSelectFilter<Integer> wantedLevelFilterChart = new MultiSelectFilter<>("WantedLevel");
    private final MultiSelectFilter<Complexity> complexityFilterChart = new MultiSelectFilter<>("Complexity");
    private final MultiSelectFilter<Status> statusFilterChart = new MultiSelectFilter<>("Status");
    private final MultiSelectFilter<Priority> priorityFilterChart = new MultiSelectFilter<>("Priority");
    private final MultiSelectFilter<Risk> riskFilterChart = new MultiSelectFilter<>("Risk");
    private final JTextField targetVersionFilterChart = new JTextField(6);
    private final JTextField commentFilterChart = new JTextField(8);

    // UI elements for filters layout
    private JPanel row2Panel;
    private JPanel row3Panel;
    private JButton toggleFiltersButton;
    private boolean filtersCollapsed = false;

    private JPanel row2PanelChart;
    private JPanel row3PanelChart;
    private JButton toggleFiltersButtonChart;
    private boolean filtersCollapsedChart = false;

    public DebtToolWindow(Project project) {
        this.project = project;
        this.debtService = project.getService(DebtService.class);
        this.debtProviderService = project.getService(DebtProviderService.class);
        this.columnService = project.getService(ColumnService.class);
        this.tableModel = new DebtTableModel(debtService, columnService);
        this.table = new JBTable(tableModel) {
            @Override
            public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
                Component c = super.prepareRenderer(renderer, row, column);
                // After rendering a cell, ensure the row height accommodates wrapped text in Description/Comment
                adjustRowHeightFor(row);
                return c;
            }
        };
        this.sorter = new TableRowSorter<>(tableModel);
        table.setRowSorter(sorter);
        this.pieChartPanel = new ModulePieChartPanel();
        this.relationshipGraphPanel = new RelationshipGraphPanel();

        // Apply wrapping renderers for text columns
        applyWrappingRenderers();

        // Re-apply wrapping and adjust heights when columns change (width/visibility/move)
        table.getColumnModel().addColumnModelListener(new TableColumnModelListener() {
            @Override
            public void columnAdded(TableColumnModelEvent e) {
                applyWrappingRenderers();
                adjustAllRowsHeight();
            }

            @Override
            public void columnRemoved(TableColumnModelEvent e) {
                applyWrappingRenderers();
                adjustAllRowsHeight();
            }

            @Override
            public void columnMoved(TableColumnModelEvent e) {
                applyWrappingRenderers();
                adjustAllRowsHeight();
            }

            @Override
            public void columnSelectionChanged(ListSelectionEvent e) {
            }

            @Override
            public void columnMarginChanged(ChangeEvent e) {
                adjustAllRowsHeight();
            }
        });

        // Configure multi-select enum filters
        complexityFilter.setOptions(Arrays.asList(Complexity.values()));
        statusFilter.setOptions(Arrays.asList(Status.values()));
        priorityFilter.setOptions(Arrays.asList(Priority.values()));
        riskFilter.setOptions(Arrays.asList(Risk.values()));

        // Configure chart tab enum filters as well
        complexityFilterChart.setOptions(Arrays.asList(Complexity.values()));
        statusFilterChart.setOptions(Arrays.asList(Status.values()));
        priorityFilterChart.setOptions(Arrays.asList(Priority.values()));
        riskFilterChart.setOptions(Arrays.asList(Risk.values()));

        JComboBox<Complexity> complexityComboBox = new JComboBox<>(Complexity.values());
        TableColumn col6 = table.getColumnModel().getColumn(6);
        col6.setCellEditor(new DefaultCellEditor(complexityComboBox));

        JComboBox<Status> statusComboBox = new JComboBox<>(Status.values());
        table.getColumnModel().getColumn(7).setCellEditor(new DefaultCellEditor(statusComboBox));

        JComboBox<Priority> priorityComboBox = new JComboBox<>(Priority.values());
        table.getColumnModel().getColumn(8).setCellEditor(new DefaultCellEditor(priorityComboBox));

        JComboBox<Risk> riskComboBox = new JComboBox<>(Risk.values());
        table.getColumnModel().getColumn(9).setCellEditor(new DefaultCellEditor(riskComboBox));


        // Action buttons (Edit + Delete) column is at index 12
        TableColumn actionCol = table.getColumnModel().getColumn(12);
        ActionButtonsCell actionButtons = new ActionButtonsCell(viewRow -> {
            int modelRow = table.convertRowIndexToModel(viewRow);
            if (modelRow >= 0 && modelRow < tableModel.getDebtItems().size()) {
                DebtItem oldItem = tableModel.getDebtItems().get(modelRow);
                // Open dialog pre-filled with selected item
                AddDebtDialog dialog = new AddDebtDialog(project, oldItem);
                if (dialog.showAndGet()) {
                    DebtItem newItem = oldItem.toBuilder()
                            .withFile(dialog.getFilePath())
                            .withLine(dialog.getLine())
                            .withTitle(dialog.getTitleText())
                            .withDescription(dialog.getDescription())
                            .withWantedLevel(dialog.getWantedLevel())
                            .withComplexity(dialog.getComplexity())
                            .withStatus(dialog.getStatus())
                            .withPriority(dialog.getPriority())
                            .withRisk(dialog.getRisk())
                            .withTargetVersion(dialog.getTargetVersion())
                            .withComment(dialog.getComment())
                            .withCurrentModule(DebtService.resolveCurrentModule(dialog.getFilePath(), project.getBasePath()))
                            .withLinks(dialog.getLinks())
                            .build();

                    LOG.info("Edit confirmed: file=" + newItem.getFile() + ":" + newItem.getLine() +
                            " title=\"" + oldItem.getTitle() + "\" -> \"" + newItem.getTitle() + "\"" +
                            " desc=\"" + oldItem.getDescription() + "\" -> \"" + newItem.getDescription() + "\"" +
                            " user=" + newItem.getUsername() +
                            " targetVersion=\"" + oldItem.getTargetVersion() + "\" -> \"" + newItem.getTargetVersion() + "\"" +
                            " comment=\"" + oldItem.getComment() + "\" -> \"" + newItem.getComment() + "\"");

                    debtService.update(oldItem, newItem);
                    updateTable();
                } else {
                    if (LOG.isDebugEnabled())
                        LOG.debug("Edit dialog canceled for file=" + oldItem.getFile() + ":" + oldItem.getLine());
                }
            }
        }, viewRow -> {
            int modelRow = table.convertRowIndexToModel(viewRow);
            if (modelRow >= 0 && modelRow < tableModel.getDebtItems().size()) {
                DebtItem debtItem = tableModel.getDebtItems().get(modelRow);
                LOG.info("Delete confirmed: file=" + debtItem.getFile() + ":" + debtItem.getLine() +
                        " title=\"" + debtItem.getTitle() + "\" user=" + debtItem.getUsername());
                debtService.remove(debtItem);
                updateTable();
            }
        });
        actionCol.setCellRenderer(actionButtons);
        actionCol.setCellEditor(actionButtons);
        // Keep action column moderately narrow; rely on compact layout inside the cell
        actionCol.setPreferredWidth(52);
        actionCol.setMaxWidth(60);
        actionCol.setMinWidth(44);

        // Capture original TableColumn instances for show/hide later
        columnService.attachTableColumns(table);

        // Apply initial visibility from settings and sync selector
        applyColumnVisibilityFromSettings();
        // Remember the default height as baseline for auto-expansion
        this.defaultRowHeight = table.getRowHeight();

        // Refresh the table automatically when settings change (e.g., username updated)
        project.getMessageBus().connect().subscribe(DebtSettings.TOPIC, new DebtSettingsListener() {
            @Override
            public void settingsChanged(DebtSettings.State settings) {
                LOG.info("Settings changed: username=" + settings.getUsername() + " relDebtPath=" + settings.getDebtFilePath(project));
                // Apply column visibility from settings
                applyColumnVisibilityFromSettings();
                // Refresh data
                updateTable();
            }
        });

        project.getMessageBus().connect().subscribe(DebtService.TOPIC, new DebtServiceListener() {
            @Override
            public void refresh() {
                if (LOG.isDebugEnabled()) LOG.debug("Refresh requested from toolwindow");
                updateTable();
            }
        });
    }

    public JPanel getContent() {
        // Build the existing list UI into its own panel
        JPanel listPanel = new JPanel(new BorderLayout());
        listPanel.add(new JBScrollPane(table), BorderLayout.CENTER);

        // Filters bar at the top
        JPanel filters = new JPanel();
        filters.setLayout(new BoxLayout(filters, BoxLayout.Y_AXIS));

        JPanel row1 = new JPanel(new BorderLayout(8, 2));
        row1.add(new JLabel("Filter :"), BorderLayout.WEST);

        // Column selector in the center of the top row
        // Options are model indices
        List<Integer> colOptions = new ArrayList<>();
        for (int i = 0; i < tableModel.getColumnCount(); i++) colOptions.add(i);
        columnSelector.setOptions(colOptions);
        columnSelector.setRenderer((Integer idx) -> {
            String name = tableModel.getColumnName(idx);
            if (name == null || name.isBlank()) return ColumnService.ACTIONS_NAME;
            return name;
        });
        // Initialize selection reflects current visibility (if all visible -> show All)
        List<Integer> visible = columnService.getVisibleModelIndices();
        if (visible.size() == tableModel.getColumnCount()) {
            columnSelector.clearSelection();
        } else {
            columnSelector.setSelected(visible);
        }
        columnSelector.addSelectionListener(this::applyColumnSelection);
        row1.add(columnSelector);

        toggleFiltersButton = new JButton("-"); // Expanded state shows "-"
        toggleFiltersButton.addActionListener(e -> {
            filtersCollapsed = !filtersCollapsed;
            if (row2Panel != null) row2Panel.setVisible(!filtersCollapsed);
            if (row3Panel != null) row3Panel.setVisible(!filtersCollapsed);
            toggleFiltersButton.setText(filtersCollapsed ? "+" : "-");
            filters.revalidate();
            filters.repaint();
        });
        row1.add(toggleFiltersButton, BorderLayout.EAST);

        row2Panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        row2Panel.add(new JLabel("File:"));
        row2Panel.add(fileFilter);
        row2Panel.add(new JLabel("Title:"));
        row2Panel.add(titleFilter);
        row2Panel.add(new JLabel("Description:"));
        row2Panel.add(descFilter);
        row2Panel.add(new JLabel("User:"));
        row2Panel.add(userFilter);
        row2Panel.add(new JLabel("TargetVersion:"));
        row2Panel.add(targetVersionFilter);
        row2Panel.add(new JLabel("Comment:"));
        row2Panel.add(commentFilter);

        row3Panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        row3Panel.add(new JLabel("WantedLevel:"));
        row3Panel.add(wantedLevelFilter);
        row3Panel.add(new JLabel("Complexity:"));
        row3Panel.add(complexityFilter);
        row3Panel.add(new JLabel("Status:"));
        row3Panel.add(statusFilter);
        row3Panel.add(new JLabel("Priority:"));
        row3Panel.add(priorityFilter);
        row3Panel.add(new JLabel("Risk:"));
        row3Panel.add(riskFilter);

        // Initial visibility based on collapsed state
        row2Panel.setVisible(!filtersCollapsed);
        row3Panel.setVisible(!filtersCollapsed);
        toggleFiltersButton.setText(filtersCollapsed ? "+" : "-");

        filters.add(row1);
        filters.add(row2Panel);
        filters.add(row3Panel);
        listPanel.add(filters, BorderLayout.NORTH);

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
        titleFilter.getDocument().addDocumentListener(docListener);
        descFilter.getDocument().addDocumentListener(docListener);
        userFilter.getDocument().addDocumentListener(docListener);
        targetVersionFilter.getDocument().addDocumentListener(docListener);
        commentFilter.getDocument().addDocumentListener(docListener);

        // Multi-select filters notify via selection listeners
        wantedLevelFilter.addSelectionListener(this::applyFilters);
        complexityFilter.addSelectionListener(this::applyFilters);
        statusFilter.addSelectionListener(this::applyFilters);
        priorityFilter.addSelectionListener(this::applyFilters);
        riskFilter.addSelectionListener(this::applyFilters);

        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2) {
                    int viewRow = table.getSelectedRow();
                    if (viewRow >= 0) {
                        int modelRow = table.convertRowIndexToModel(viewRow);
                        DebtItem debtItem = tableModel.getDebtItems().get(modelRow);
                        String stored = debtItem.getFile();
                        int line = debtItem.getLine();
                        String absolutePath = stored;
                        try {
                            Path relPath = Paths.get(stored);
                            if (relPath.isAbsolute()) {
                                absolutePath = relPath.toAbsolutePath().normalize().toString();
                            } else {
                                final Map<Repository, List<DebtItem>> debtsByRepository = debtService.getDebtsByRepository();
                                final String repoRoot = debtsByRepository.entrySet()
                                        .stream()
                                        .filter(entry -> entry.getValue().contains(debtItem))
                                        .findFirst()
                                        .map(Map.Entry::getKey)
                                        .map(Repository::getRepositoryAbsolutePath)
                                        .orElseThrow();

                                absolutePath = Paths.get(repoRoot).resolve(relPath).normalize().toString();
                            }
                        } catch (Exception ex) {
                            if (LOG.isDebugEnabled())
                                LOG.debug("Failed to resolve path for navigation. stored=" + stored + " msg=" + ex.getMessage(), ex);
                        }
                        if (LOG.isDebugEnabled())
                            LOG.debug("Navigate request: stored=" + stored + " resolved=" + absolutePath + ":" + line);

                        VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(absolutePath);
                        if (virtualFile != null) {
                            OpenFileDescriptor descriptor = new OpenFileDescriptor(project, virtualFile, line - 1, 0);
                            FileEditorManager.getInstance(project).openTextEditor(descriptor, true);
                        } else {
                            LOG.warn("Navigation target not found. path=" + absolutePath);
                        }
                    }
                }
            }
        });

        // Bottom buttons panel (export only; refresh moved to toolwindow title bar)
        JPanel bottomButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton exportButton = new JButton("Export XLSX");
        exportButton.addActionListener(e -> exportDebtItemsToXlsx());
        bottomButtons.add(exportButton);
        listPanel.add(bottomButtons, BorderLayout.SOUTH);

        // Build the chart tab with its own filters
        JPanel chartPanel = new JPanel(new BorderLayout());

        JPanel chartFilters = new JPanel();
        chartFilters.setLayout(new BoxLayout(chartFilters, BoxLayout.Y_AXIS));

        JPanel row1Chart = new JPanel(new BorderLayout(8, 2));
        row1Chart.add(new JLabel("Filter :"), BorderLayout.WEST);
        toggleFiltersButtonChart = new JButton("-");
        toggleFiltersButtonChart.addActionListener(e -> {
            filtersCollapsedChart = !filtersCollapsedChart;
            if (row2PanelChart != null) row2PanelChart.setVisible(!filtersCollapsedChart);
            if (row3PanelChart != null) row3PanelChart.setVisible(!filtersCollapsedChart);
            toggleFiltersButtonChart.setText(filtersCollapsedChart ? "+" : "-");
            chartFilters.revalidate();
            chartFilters.repaint();
        });
        row1Chart.add(toggleFiltersButtonChart, BorderLayout.EAST);

        row2PanelChart = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        row2PanelChart.add(new JLabel("File:"));
        row2PanelChart.add(fileFilterChart);
        row2PanelChart.add(new JLabel("Title:"));
        row2PanelChart.add(titleFilterChart);
        row2PanelChart.add(new JLabel("Description:"));
        row2PanelChart.add(descFilterChart);
        row2PanelChart.add(new JLabel("User:"));
        row2PanelChart.add(userFilterChart);
        row2PanelChart.add(new JLabel("TargetVersion:"));
        row2PanelChart.add(targetVersionFilterChart);
        row2PanelChart.add(new JLabel("Comment:"));
        row2PanelChart.add(commentFilterChart);

        row3PanelChart = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        row3PanelChart.add(new JLabel("WantedLevel:"));
        row3PanelChart.add(wantedLevelFilterChart);
        row3PanelChart.add(new JLabel("Complexity:"));
        row3PanelChart.add(complexityFilterChart);
        row3PanelChart.add(new JLabel("Status:"));
        row3PanelChart.add(statusFilterChart);
        row3PanelChart.add(new JLabel("Priority:"));
        row3PanelChart.add(priorityFilterChart);
        row3PanelChart.add(new JLabel("Risk:"));
        row3PanelChart.add(riskFilterChart);

        // Initial visibility
        row2PanelChart.setVisible(!filtersCollapsedChart);
        row3PanelChart.setVisible(!filtersCollapsedChart);
        toggleFiltersButtonChart.setText(filtersCollapsedChart ? "+" : "-");

        chartFilters.add(row1Chart);
        chartFilters.add(row2PanelChart);
        chartFilters.add(row3PanelChart);
        chartPanel.add(chartFilters, BorderLayout.NORTH);

        // Wire chart filter listeners
        DocumentListener docListenerChart = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                updateChart();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updateChart();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                updateChart();
            }
        };
        fileFilterChart.getDocument().addDocumentListener(docListenerChart);
        titleFilterChart.getDocument().addDocumentListener(docListenerChart);
        descFilterChart.getDocument().addDocumentListener(docListenerChart);
        userFilterChart.getDocument().addDocumentListener(docListenerChart);
        targetVersionFilterChart.getDocument().addDocumentListener(docListenerChart);
        commentFilterChart.getDocument().addDocumentListener(docListenerChart);

        wantedLevelFilterChart.addSelectionListener(this::updateChart);
        complexityFilterChart.addSelectionListener(this::updateChart);
        statusFilterChart.addSelectionListener(this::updateChart);
        priorityFilterChart.addSelectionListener(this::updateChart);
        riskFilterChart.addSelectionListener(this::updateChart);

        chartPanel.add(new JBScrollPane(pieChartPanel), BorderLayout.CENTER);

        // Root with tabs
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("debts", listPanel);
        tabs.addTab("modules", chartPanel);
        tabs.addTab("RelationShip", new JBScrollPane(relationshipGraphPanel));

        JPanel root = new JPanel(new BorderLayout());
        root.add(tabs, BorderLayout.CENTER);

        updateTable();
        return root;
    }

    private void applyFilters() {
        List<RowFilter<DebtTableModel, Object>> filters = new ArrayList<>();

        addTextFilter(filters, fileFilter.getText(), 0);
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
            LOG.debug("Filters applied. active=" + actives + " visibleRows=" + table.getRowCount());
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

    private void addTextFilter(List<RowFilter<DebtTableModel, Object>> filters, String text, int column) {
        if (text != null && !text.isBlank()) {
            String expr = "(?i)" + Pattern.quote(text.trim());
            filters.add(RowFilter.regexFilter(expr, column));
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

    private void updateTable() {
        long start = System.currentTimeMillis();
        tableModel.clearAll();
        allItems.clear();

        final TreeSet<Integer> wantedLevels = new TreeSet<>(Comparator.naturalOrder());

        for (DebtItem item : debtProviderService.currentItems()) {
            tableModel.addDebtItem(item);
            allItems.add(item);
            wantedLevels.add(item.getWantedLevel());
        }

        // Populate wanted level options for both tabs
        wantedLevelFilter.setOptions(wantedLevels);
        wantedLevelFilterChart.setOptions(wantedLevels);

        // Apply filters to table and update chart aggregation
        applyFilters();
        // Ensure row heights match wrapped content after data refresh
        adjustAllRowsHeight();
        int visible = table.getRowCount();
        updateChart();
        if (LOG.isDebugEnabled()) {
            LOG.debug("updateTable completed in " + (System.currentTimeMillis() - start) + " ms. total=" + allItems.size() + " visible=" + visible);
        }
    }

    private void applyColumnVisibilityFromSettings() {
        try {
            Map<String, Boolean> vis = project.getService(com.github.fligneul.debtplugin.debt.settings.DebtSettings.class).getState().getColumnVisibility();
            columnService.setVisibilityByName(vis);
            columnService.applyTo(table);
            // Apply wrapping again because columns might have been re-added/removed
            applyWrappingRenderers();
            adjustAllRowsHeight();
            // Sync selector selection to currently visible columns
            List<Integer> visible = columnService.getVisibleModelIndices();
            if (visible.size() == tableModel.getColumnCount()) {
                columnSelector.clearSelection();
            } else {
                columnSelector.setSelected(visible);
            }
        } catch (Exception ignored) {
        }
    }

    private void updateChart() {
        // Aggregate modules from items that match the chart-specific filters
        final LinkedHashMap<String, Integer> byModule = new LinkedHashMap<>();
        for (DebtItem it : allItems) {
            if (!matchesChart(it)) continue;
            String module = it.getCurrentModule();
            if (module == null || module.isBlank()) module = "Unknown";
            byModule.put(module, byModule.getOrDefault(module, 0) + 1);
        }
        pieChartPanel.setData(byModule);
        relationshipGraphPanel.setData(allItems);
    }

    private boolean matchesChart(DebtItem it) {
        // Text fields (contains, case-insensitive)
        String fileNeedle = fileFilterChart.getText();
        if (!(containsIgnoreCase(it.getFile(), fileNeedle) || containsIgnoreCase(getBaseName(it.getFile()), fileNeedle)))
            return false; // match path or base name
        if (!containsIgnoreCase(it.getTitle(), titleFilterChart.getText())) return false;
        if (!containsIgnoreCase(it.getDescription(), descFilterChart.getText())) return false;
        if (!containsIgnoreCase(it.getUsername(), userFilterChart.getText())) return false;
        if (!containsIgnoreCase(it.getTargetVersion(), targetVersionFilterChart.getText())) return false;
        if (!containsIgnoreCase(it.getComment(), commentFilterChart.getText())) return false;

        // Multi-select exact matches when any selected
        if (!wantedLevelFilterChart.getSelected().isEmpty() && !wantedLevelFilterChart.getSelected().contains(it.getWantedLevel()))
            return false;
        if (!complexityFilterChart.getSelected().isEmpty() && !complexityFilterChart.getSelected().contains(it.getComplexity()))
            return false;
        if (!statusFilterChart.getSelected().isEmpty() && !statusFilterChart.getSelected().contains(it.getStatus()))
            return false;
        if (!priorityFilterChart.getSelected().isEmpty() && !priorityFilterChart.getSelected().contains(it.getPriority()))
            return false;
        if (!riskFilterChart.getSelected().isEmpty() && !riskFilterChart.getSelected().contains(it.getRisk()))
            return false;

        return true;
    }

    private boolean containsIgnoreCase(String haystack, String needle) {
        if (needle == null || needle.isBlank()) return true;
        String n = needle.trim().toLowerCase();
        String h = haystack == null ? "" : haystack.toLowerCase();
        return h.contains(n);
    }

    private String getBaseName(String path) {
        if (path == null) return "";
        String p = path.replace('\\', '/');
        int idx = p.lastIndexOf('/');
        return idx >= 0 ? p.substring(idx + 1) : p;
    }

    private void applyWrappingRenderers() {
        setWrappingRendererForModelColumn(3);  // Description
        setWrappingRendererForModelColumn(11); // Comment
    }

    private void setWrappingRendererForModelColumn(int modelIndex) {
        int viewIdx = table.convertColumnIndexToView(modelIndex);
        if (viewIdx < 0) return;
        table.getColumnModel().getColumn(viewIdx).setCellRenderer(new WrappingTextCellRenderer());
    }

    private void adjustRowHeightFor(int viewRow) {
        if (viewRow < 0 || viewRow >= table.getRowCount()) return;
        int maxHeight = defaultRowHeight > 0 ? defaultRowHeight : table.getRowHeight();
        maxHeight = Math.max(maxHeight, preferredHeightForCell(viewRow, 3)); // Description
        maxHeight = Math.max(maxHeight, preferredHeightForCell(viewRow, 11)); // Comment
        if (maxHeight != table.getRowHeight(viewRow)) {
            table.setRowHeight(viewRow, maxHeight);
        }
    }

    private int preferredHeightForCell(int viewRow, int modelCol) {
        int viewCol = table.convertColumnIndexToView(modelCol);
        if (viewCol < 0) return 0;
        Object value = table.getValueAt(viewRow, viewCol);
        TableCellRenderer renderer = table.getCellRenderer(viewRow, viewCol);
        Component component = renderer.getTableCellRendererComponent(table, value, false, false, viewRow, viewCol);
        int colWidth = table.getColumnModel().getColumn(viewCol).getWidth();

        component.setSize(new Dimension(colWidth, Integer.MAX_VALUE));
        return Math.max(component.getPreferredSize().height, defaultRowHeight);
    }

    private void adjustAllRowsHeight() {
        int rc = table.getRowCount();
        for (int r = 0; r < rc; r++) adjustRowHeightFor(r);
    }

    private static class WrappingTextCellRenderer extends JTextArea implements TableCellRenderer {
        public WrappingTextCellRenderer() {
            setLineWrap(true);
            setWrapStyleWord(true);
            setOpaque(true);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            setText(value == null ? "" : String.valueOf(value));
            setFont(table.getFont());
            if (isSelected) {
                setForeground(table.getSelectionForeground());
                setBackground(table.getSelectionBackground());
            } else {
                setForeground(table.getForeground());
                setBackground(table.getBackground());
            }
            int colWidth = table.getColumnModel().getColumn(column).getWidth();
            setSize(new Dimension(colWidth, Integer.MAX_VALUE));
            return this;
        }
    }

    private void exportDebtItemsToXlsx() {
        List<DebtItem> items = debtProviderService.currentItems();

        JFileChooser chooser = new JFileChooser(project.getBasePath());
        chooser.setDialogTitle("Export Debt Items to XLSX");
        chooser.setFileFilter(new FileNameExtensionFilter("Excel Workbook (*.xlsx)", "xlsx"));
        chooser.setSelectedFile(new File("debt-items.xlsx"));

        int res = chooser.showSaveDialog(SwingUtilities.getWindowAncestor(table));
        if (res != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File selected = chooser.getSelectedFile();
        if (selected == null) return;
        if (!selected.getName().toLowerCase().endsWith(".xlsx")) {
            selected = new File(selected.getParentFile(), selected.getName() + ".xlsx");
        }

        if (selected.exists()) {
            int answer = Messages.showYesNoDialog(
                    project,
                    "File already exists. Do you want to overwrite it?\n" + selected.getAbsolutePath(),
                    "Overwrite File?",
                    null
            );
            if (answer != Messages.YES) {
                return;
            }
        }

        LOG.info("Export started to: " + selected.getAbsolutePath());
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Debt Items");

            // Header row
            String[] headers = new String[]{
                    "File", "Line", "Title", "Description", "User", "WantedLevel",
                    "Complexity", "Status", "Priority", "Risk", "TargetVersion", "Comment", "CurrentModule"
            };
            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(headers[i]);
            }

            // Data rows
            int rowIdx = 1;
            for (DebtItem it : items) {
                Row row = sheet.createRow(rowIdx++);
                int c = 0;
                row.createCell(c++).setCellValue(it.getFile());
                row.createCell(c++).setCellValue(it.getLine());
                row.createCell(c++).setCellValue(it.getTitle());
                row.createCell(c++).setCellValue(it.getDescription());
                row.createCell(c++).setCellValue(it.getUsername());
                row.createCell(c++).setCellValue(it.getWantedLevel());
                row.createCell(c++).setCellValue(String.valueOf(it.getComplexity()));
                row.createCell(c++).setCellValue(String.valueOf(it.getStatus()));
                row.createCell(c++).setCellValue(String.valueOf(it.getPriority()));
                row.createCell(c++).setCellValue(String.valueOf(it.getRisk()));
                row.createCell(c++).setCellValue(it.getTargetVersion());
                row.createCell(c++).setCellValue(it.getComment());
                row.createCell(c).setCellValue(it.getCurrentModule());
            }

            // Autosize
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            // Ensure parent dir exists
            File parent = selected.getParentFile();
            if (parent != null && !parent.exists()) {
                //noinspection ResultOfMethodCallIgnored
                parent.mkdirs();
            }

            try (FileOutputStream fos = new FileOutputStream(selected)) {
                workbook.write(fos);
            }

            LOG.info("Export completed. count=" + items.size() + " path=" + selected.getAbsolutePath());
            Messages.showInfoMessage(project, "Exported " + items.size() + " item(s) to:\n" + selected.getAbsolutePath(), "Export Successful");
        } catch (Exception ex) {
            LOG.error("Export failed. path=" + selected.getAbsolutePath() + " message=" + ex.getMessage(), ex);
            Messages.showErrorDialog(project, "Failed to export XLSX:\n" + ex.getMessage(), "Export Failed");
        }
    }
}
