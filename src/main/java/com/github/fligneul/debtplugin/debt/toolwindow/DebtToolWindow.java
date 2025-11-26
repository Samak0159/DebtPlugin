package com.github.fligneul.debtplugin.debt.toolwindow;

import com.github.fligneul.debtplugin.debt.model.Complexity;
import com.github.fligneul.debtplugin.debt.model.DebtItem;
import com.github.fligneul.debtplugin.debt.model.Priority;
import com.github.fligneul.debtplugin.debt.model.Risk;
import com.github.fligneul.debtplugin.debt.model.Status;
import com.github.fligneul.debtplugin.debt.service.DebtService;
import com.github.fligneul.debtplugin.debt.settings.DebtSettings;
import com.github.fligneul.debtplugin.debt.settings.DebtSettingsListener;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import com.intellij.util.containers.OrderedSet;

import javax.swing.BoxLayout;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.RowFilter;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.TableColumn;
import javax.swing.table.TableRowSorter;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

public class DebtToolWindow {
    private final Project project;
    private final DebtService debtService;
    private final DebtTableModel tableModel;
    private final JBTable table;
    private final TableRowSorter<DebtTableModel> sorter;

    // Filter inputs
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

    // UI elements for filters layout
    private JPanel row2Panel;
    private JPanel row3Panel;
    private JButton toggleFiltersButton;
    private boolean filtersCollapsed = false;

    public DebtToolWindow(Project project) {
        this.project = project;
        this.debtService = project.getService(DebtService.class);
        this.tableModel = new DebtTableModel(debtService);
        this.table = new JBTable(tableModel);
        this.sorter = new TableRowSorter<>(tableModel);
        table.setRowSorter(sorter);

        // Configure multi-select enum filters
        complexityFilter.setOptions(Arrays.asList(Complexity.values()));
        statusFilter.setOptions(Arrays.asList(Status.values()));
        priorityFilter.setOptions(Arrays.asList(Priority.values()));
        riskFilter.setOptions(Arrays.asList(Risk.values()));

        JComboBox<Complexity> complexityComboBox = new JComboBox<>(Complexity.values());
        TableColumn col6 = table.getColumnModel().getColumn(6);
        col6.setCellEditor(new DefaultCellEditor(complexityComboBox));

        JComboBox<Status> statusComboBox = new JComboBox<>(Status.values());
        table.getColumnModel().getColumn(7).setCellEditor(new DefaultCellEditor(statusComboBox));

        JComboBox<Priority> priorityComboBox = new JComboBox<>(Priority.values());
        table.getColumnModel().getColumn(8).setCellEditor(new DefaultCellEditor(priorityComboBox));

        JComboBox<Risk> riskComboBox = new JComboBox<>(Risk.values());
        table.getColumnModel().getColumn(9).setCellEditor(new DefaultCellEditor(riskComboBox));


        // Delete button column is at index 12
        TableColumn actionCol = table.getColumnModel().getColumn(12);
        actionCol.setCellRenderer(new DeleteButtonCell(viewRow -> {
            int modelRow = table.convertRowIndexToModel(viewRow);
            if (modelRow >= 0 && modelRow < tableModel.debtItems.size()) {
                DebtItem debtItem = tableModel.debtItems.get(modelRow);
                debtService.remove(debtItem);
                updateTable();
            }
        }));
        actionCol.setCellEditor(new DeleteButtonCell(viewRow -> {
            int modelRow = table.convertRowIndexToModel(viewRow);
            if (modelRow >= 0 && modelRow < tableModel.debtItems.size()) {
                DebtItem debtItem = tableModel.debtItems.get(modelRow);
                debtService.remove(debtItem);
                updateTable();
            }
        }));
        actionCol.setPreferredWidth(30);
        actionCol.setMaxWidth(30);
        actionCol.setMinWidth(30);

        // Refresh the table automatically when settings change (e.g., username updated)
        project.getMessageBus().connect().subscribe(DebtSettings.TOPIC, new DebtSettingsListener() {
            @Override
            public void settingsChanged(DebtSettings.State settings) {
                updateTable();
            }
        });

        // Ensure the file exists is not required to build; keep the reference if needed
        VirtualFile debtFile = LocalFileSystem.getInstance().findFileByIoFile(debtService.getDebtFile());
    }

    public JPanel getContent() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(new JBScrollPane(table), BorderLayout.CENTER);

        // Filters bar at the top
        JPanel filters = new JPanel();
        filters.setLayout(new BoxLayout(filters, BoxLayout.Y_AXIS));

        JPanel row1 = new JPanel(new BorderLayout(8, 2));
        row1.add(new JLabel("Filter :"), BorderLayout.WEST);
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
        panel.add(filters, BorderLayout.NORTH);

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
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int viewRow = table.getSelectedRow();
                    if (viewRow >= 0) {
                        int modelRow = table.convertRowIndexToModel(viewRow);
                        String stored = tableModel.debtItems.get(modelRow).getFile();
                        int line = tableModel.debtItems.get(modelRow).getLine();
                        String basePath = project.getBasePath();
                        String absolutePath = stored;
                        try {
                            Path p = Paths.get(stored);
                            if (basePath != null && !p.isAbsolute()) {
                                absolutePath = Paths.get(basePath).resolve(p).normalize().toString();
                            } else {
                                absolutePath = p.toAbsolutePath().normalize().toString();
                            }
                        } catch (Exception ignored) {
                        }
                        VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(absolutePath);
                        if (virtualFile != null) {
                            OpenFileDescriptor descriptor = new OpenFileDescriptor(project, virtualFile, line - 1, 0);
                            FileEditorManager.getInstance(project).openTextEditor(descriptor, true);
                        }
                    }
                }
            }
        });

        JButton refreshButton = new JButton("Refresh");
        refreshButton.addActionListener(e -> updateTable());
        panel.add(refreshButton, BorderLayout.SOUTH);

        updateTable();
        return panel;
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
        tableModel.clearAll();

        final TreeSet<Integer> wantedLevels = new TreeSet<>(Comparator.naturalOrder());

        for (DebtItem item : debtService.all()) {
            tableModel.addDebtItem(item);
            wantedLevels.add(item.getWantedLevel());
        }

        wantedLevelFilter.setOptions(wantedLevels);

        applyFilters();
    }
}
