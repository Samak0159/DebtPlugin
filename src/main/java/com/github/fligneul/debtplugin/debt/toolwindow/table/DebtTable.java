package com.github.fligneul.debtplugin.debt.toolwindow.table;

import com.github.fligneul.debtplugin.debt.action.AddDebtDialog;
import com.github.fligneul.debtplugin.debt.model.Complexity;
import com.github.fligneul.debtplugin.debt.model.DebtItem;
import com.github.fligneul.debtplugin.debt.model.Repository;
import com.github.fligneul.debtplugin.debt.model.Risk;
import com.github.fligneul.debtplugin.debt.model.Status;
import com.github.fligneul.debtplugin.debt.service.ColumnService;
import com.github.fligneul.debtplugin.debt.service.DebtProviderService;
import com.github.fligneul.debtplugin.debt.service.DebtService;
import com.github.fligneul.debtplugin.debt.settings.DebtSettings;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.EditorColorsListener;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.table.JBTable;
import org.jetbrains.annotations.Nullable;

import javax.swing.DefaultCellEditor;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.TableColumnModelEvent;
import javax.swing.event.TableColumnModelListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.PlainDocument;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.Serial;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

public class DebtTable extends JBTable {
    private static final Logger LOG = Logger.getInstance(DebtTable.class);

    private final Project project;
    private final DebtService debtService;
    private final ColumnService columnService;
    private final DebtProviderService debtProviderService;
    private final DebtSettings settings;
    private final DebtTableModel tableModel;
    private final int defaultRowHeight;
    private final JComboBox<String> priorityComboBox = new JComboBox<>();
    private final JComboBox<String> typeComboBox = new JComboBox<>();
    private final AtomicReference<Color> baseColorAtomic = new AtomicReference<>(this.getBackground());

    public DebtTable(final Project project,
                     final DebtService debtService,
                     final ColumnService columnService,
                     final Consumer<Integer> nbDebtsConsumer) {
        this.project = project;
        this.debtService = debtService;
        this.columnService = columnService;
        this.debtProviderService = project.getService(DebtProviderService.class);
        this.settings = project.getService(DebtSettings.class);

        this.tableModel = new DebtTableModel(debtService, columnService);
        this.setModel(tableModel);
        nbDebtsConsumer.accept(this.getRowCount());

        // Re-apply wrapping and adjust heights when columns change (width/visibility/move)
        this.getColumnModel().addColumnModelListener(new DebtColumnModelListener(this));

        initBackgroundColor(project);

        initColumns();

        // Capture original TableColumn instances for show/hide later
        columnService.attachTableColumns(this);

        // Remember the default height as baseline for auto-expansion
        this.defaultRowHeight = this.getRowHeight();

        // Apply wrapping renderers for text columns
        applyWrappingRenderers();

        // Apply initial visibility from settings and sync selector
        applyColumnVisibilityFromSettings();

        this.addMouseListener(new DebtMouseAdapter(project, this, debtService, tableModel));
    }

    private void initBackgroundColor(final Project project) {

        project.getMessageBus().connect().subscribe(EditorColorsManager.TOPIC, new EditorColorsListener() {
            @Override
            public void globalSchemeChange(@Nullable final EditorColorsScheme editorColorsScheme) {
                if (editorColorsScheme == null) {
                    baseColorAtomic.set(DebtTable.this.getBackground());
                } else {
                    baseColorAtomic.set(editorColorsScheme.getDefaultBackground());
                }

                DebtTable.this.repaint();
            }
        });

        this.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                final Component component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

                if (EditorColorsManager.getInstance().isDarkEditor()) {
                    if (row % 2 == 0) {
                        component.setBackground(baseColorAtomic.get());
                    } else {
                        component.setBackground(baseColorAtomic.get().brighter());
                    }
                } else {
                    if (row % 2 == 0) {
                        component.setBackground(baseColorAtomic.get());
                    } else {
                        component.setBackground(baseColorAtomic.get().darker());
                    }
                }
                return component;
            }
        });
    }

    private static class TextAreaCellEditor extends DefaultCellEditor {
        public TextAreaCellEditor() {
            super(new JTextField());

            final JBTextArea textArea = new JBTextArea();

            final EditorDelegate editor = new EditorDelegate() {
                public void setValue(Object value) {
                    textArea.setText((value == null) ? "" : value.toString());
                }

                public Object getCellEditorValue() {
                    return textArea.getText();
                }
            };

            editorComponent = textArea;
            clickCountToStart = 2;
            delegate = editor;
        }
    }

    private void initColumns() {
        this.getColumnModel().getColumn(4).setCellEditor(new TextAreaCellEditor());

        final JComboBox<Complexity> complexityComboBox = new JComboBox<>(Complexity.values());
        this.getColumnModel().getColumn(7).setCellEditor(new DefaultCellEditor(complexityComboBox));

        final JComboBox<Status> statusComboBox = new JComboBox<>(Status.values());
        this.getColumnModel().getColumn(8).setCellEditor(new DefaultCellEditor(statusComboBox));

        this.getColumnModel().getColumn(9).setCellEditor(new DefaultCellEditor(priorityComboBox));

        final JComboBox<Risk> riskComboBox = new JComboBox<>(Risk.values());
        this.getColumnModel().getColumn(10).setCellEditor(new DefaultCellEditor(riskComboBox));

        this.getColumnModel().getColumn(12).setCellEditor(new TextAreaCellEditor());

        this.getColumnModel().getColumn(16).setCellEditor(new DefaultCellEditor(typeComboBox));
        final TableCellRenderer cellRenderer = new TableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(final JTable table, final Object value, final boolean isSelected, final boolean hasFocus, final int row, final int column) {
                if (value instanceof Long timestamp) {
                    final String date = convertTimeStampToPrintableDate(timestamp, settings);

                    return new JLabel(date);
                } else {
                    return null;
                }
            }
        };
        this.getColumnModel().getColumn(17).setCellEditor(new DateCellEditor(new JTextField(), settings));
        this.getColumnModel().getColumn(17).setCellRenderer(cellRenderer);
        this.getColumnModel().getColumn(18).setCellRenderer(cellRenderer);
        this.getColumnModel().getColumn(18).setCellEditor(new DateCellEditor(new JTextField(), settings));

        // Action buttons (Edit + Delete) column is at index 18
        final TableColumn actionCol = this.getColumnModel().getColumn(19);
        final ActionButtonsCell actionButtons = new ActionButtonsCell(editAction(), deleteAction());
        actionCol.setCellRenderer(actionButtons);
        actionCol.setCellEditor(actionButtons);
        // Keep action column moderately narrow; rely on compact layout inside the cell
        actionCol.setPreferredWidth(52);
        actionCol.setMaxWidth(60);
        actionCol.setMinWidth(44);
    }

    private IntConsumer editAction() {
        return viewRow -> {
            int modelRow = this.convertRowIndexToModel(viewRow);
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
                            .withEstimation(dialog.getEstimation())
                            .withCurrentModule(DebtService.resolveCurrentModule(dialog.getFilePath(), project.getBasePath()))
                            .withLinks(dialog.getLinks())
                            .withJira(dialog.getJira())
                            .withType(dialog.getType())
                            .build();

                    LOG.info("Edit confirmed: debt=" + newItem);

                    debtService.update(oldItem, newItem);
                    updateTable();
                } else {
                    if (LOG.isDebugEnabled())
                        LOG.debug("Edit dialog canceled for file=" + oldItem.getFile() + ":" + oldItem.getLine());
                }
            }
        };
    }

    private IntConsumer deleteAction() {
        return viewRow -> {
            int modelRow = this.convertRowIndexToModel(viewRow);
            if (modelRow >= 0 && modelRow < tableModel.getDebtItems().size()) {
                final DebtItem debtItem = tableModel.remove(modelRow);
                LOG.info("Delete confirmed: Debt=" + debtItem);
                debtService.remove(debtItem);
                updateTable();
            }
        };
    }

    public void updateTable() {
        updateTable(false);
    }

    public void updateTable(boolean refreshColumnVisibilty) {
        priorityComboBox.removeAllItems();

        priorityComboBox.removeAllItems();
        debtService.getDistinctPriorities(debtProviderService.currentItems(), priorityComboBox::addItem);
        typeComboBox.removeAllItems();
        debtService.getDistinctType(debtProviderService.currentItems(), typeComboBox::addItem);

        if (refreshColumnVisibilty) {
            applyColumnVisibilityFromSettings();
        }

        // Ensure row heights match wrapped content after data refresh
        adjustAllRowsHeight();
    }

    private int preferredHeightForCell(int viewRow, int modelCol) {
        int viewCol = this.convertColumnIndexToView(modelCol);
        if (viewCol < 0) return 0;
        Object value = this.getValueAt(viewRow, viewCol);
        TableCellRenderer renderer = this.getCellRenderer(viewRow, viewCol);
        Component component = renderer.getTableCellRendererComponent(this, value, false, false, viewRow, viewCol);
        int colWidth = this.getColumnModel().getColumn(viewCol).getWidth();

        component.setSize(new Dimension(colWidth, Integer.MAX_VALUE));
        return Math.max(component.getPreferredSize().height, defaultRowHeight);
    }

    private void applyWrappingRenderers() {
        setWrappingRendererForModelColumn(4);  // Description
        setWrappingRendererForModelColumn(12); // Comment
    }

    private void setWrappingRendererForModelColumn(int modelIndex) {
        int viewIdx = this.convertColumnIndexToView(modelIndex);
        if (viewIdx < 0) return;
        this.getColumnModel().getColumn(viewIdx).setCellRenderer(new WrappingTextCellRenderer(settings));
    }

    private void adjustRowHeightFor(int viewRow) {
        if (viewRow < 0 || viewRow >= this.getRowCount()) return;
        int maxHeight = defaultRowHeight > 0 ? defaultRowHeight : this.getRowHeight();
        maxHeight = Math.max(maxHeight, preferredHeightForCell(viewRow, 4)); // Description
        maxHeight = Math.max(maxHeight, preferredHeightForCell(viewRow, 12)); // Comment
        if (maxHeight != this.getRowHeight(viewRow)) {
            this.setRowHeight(viewRow, maxHeight);
        }
    }

    private void adjustAllRowsHeight() {
        int rc = this.getRowCount();
        for (int r = 0; r < rc; r++) adjustRowHeightFor(r);
    }

    private void applyColumnVisibilityFromSettings() {
        try {
            Map<String, Boolean> vis = project.getService(DebtSettings.class).getState().getColumnVisibility();
            columnService.setVisibilityByName(vis);
            columnService.applyTo(this);
            // Apply wrapping again because columns might have been re-added/removed
            applyWrappingRenderers();
            adjustAllRowsHeight();
        } catch (Exception ignored) {
        }
    }

    public DebtTableModel getTableModel() {
        return tableModel;
    }

    @Override
    public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
        Component c = super.prepareRenderer(renderer, row, column);
        // After rendering a cell, ensure the row height accommodates wrapped text in Description/Comment
        adjustRowHeightFor(row);
        return c;
    }

    public static class DebtColumnModelListener implements TableColumnModelListener {
        private final DebtTable debtTable;

        public DebtColumnModelListener(final DebtTable debtTable) {
            this.debtTable = debtTable;
        }

        @Override
        public void columnAdded(TableColumnModelEvent e) {
            debtTable.applyWrappingRenderers();
            debtTable.adjustAllRowsHeight();
        }

        @Override
        public void columnRemoved(TableColumnModelEvent e) {
            debtTable.applyWrappingRenderers();
            debtTable.adjustAllRowsHeight();
        }

        @Override
        public void columnMoved(TableColumnModelEvent e) {
            debtTable.applyWrappingRenderers();
            debtTable.adjustAllRowsHeight();
        }

        @Override
        public void columnSelectionChanged(ListSelectionEvent e) {
        }

        @Override
        public void columnMarginChanged(ChangeEvent e) {
            debtTable.adjustAllRowsHeight();
        }
    }

    private static class DebtMouseAdapter extends MouseAdapter {
        private final Project project;
        private final JBTable table;
        private final DebtService debtService;
        private final DebtTableModel tableModel;

        public DebtMouseAdapter(final Project project, JBTable table, final DebtService debtService, final DebtTableModel tableModel) {
            this.project = project;
            this.table = table;
            this.debtService = debtService;
            this.tableModel = tableModel;
        }

        @Override
        public void mouseClicked(MouseEvent event) {
            if (event.getClickCount() == 2) {
                int viewRow = table.getSelectedRow();
                int viewColumn = table.getSelectedColumn();
                if (viewRow >= 0 && viewColumn >= 0) {
                    int modelRow = table.convertRowIndexToModel(viewRow);
                    int modelColumn = table.convertColumnIndexToModel(viewColumn);
                    DebtItem debtItem = tableModel.getDebtItems().get(modelRow);

                    if (modelColumn == 0) {
                        String id = debtItem.getId();
                        StringSelection selection = new StringSelection(id);
                        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);
                        return;
                    }

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
    }

    private static class WrappingTextCellRenderer extends JTextArea implements TableCellRenderer {
        public WrappingTextCellRenderer(final DebtSettings settings) {
            setLineWrap(true);
            setWrapStyleWord(true);

            this.setDocument(new PlainDocument() {
                @Serial
                private static final long serialVersionUID = 1L;

                @Override
                public void insertString(int offs, String str, AttributeSet a) throws BadLocationException {
                    super.insertString(offs, str, a);

                    //has the length been exceeded
                    final int max = settings.getState().getMaxCharTextArea();

                    if (getLength() > max) {
                        //remove the extra characters.
                        //need to take into account the ellipsis, which is three characters.
                        super.remove(max - 3, getLength() - max + 3);

                        //insert ellipsis
                        super.insertString(getLength(), "...", a);
                    }
                }
            });
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

    private static class DateCellEditor extends DefaultCellEditor {
        public DateCellEditor(JTextField textField, DebtSettings settings) {
            super(textField);
            delegate = new EditorDelegate() {
                public void setValue(Object value) {
                    if (value instanceof Long timestamp) {
                        final String date = convertTimeStampToPrintableDate(timestamp, settings);
                        textField.setText(date);
                    } else {
                        LOG.error("the %s is not a long instance, can't display it".formatted(value));
                    }
                }

                public Object getCellEditorValue() {
                    String userInput = textField.getText();

                    String pattern = settings.getState().getDatePattern();
                    final boolean isPatternHasHour = pattern.contains("HH");
                    final boolean isPatternHasMinutes = pattern.contains("mm");
                    final boolean isPatternHasSeconds = pattern.contains("ss");

                    try {
                        if (isPatternHasHour || isPatternHasMinutes || isPatternHasSeconds) {
                            if (!isPatternHasHour) {
                                pattern += "HH";
                                userInput += "00";
                            }

                            if (!isPatternHasMinutes) {
                                pattern += "mm";
                                userInput += "00";
                            }

                            if (!isPatternHasSeconds) {
                                pattern += "ss";
                                userInput += "00";
                            }

                            return LocalDateTime.parse(userInput, getDateTimeFormatter(pattern))
                                    .atZone(getZoneId())
                                    .toInstant()
                                    .getEpochSecond();

                        } else {
                            return LocalDate.parse(userInput, getDateTimeFormatter(settings.getState().getDatePattern()))
                                    .atStartOfDay(getZoneId())
                                    .toEpochSecond();
                        }
                    } catch (DateTimeParseException e) {
                        LOG.error("impossible to read the value %s as a date with the pattern %s".formatted(userInput, settings.getState().getDatePattern()));
                        return 0;
                    }
                }
            };
            textField.addActionListener(delegate);
        }
    }


    private static String convertTimeStampToPrintableDate(final Long timestamp, final DebtSettings settings) {
        final Instant currentValue = Instant.ofEpochSecond(timestamp);

        final DateTimeFormatter formatter = getDateTimeFormatter(settings.getState().getDatePattern());

        return formatter.format(currentValue);
    }

    private static DateTimeFormatter getDateTimeFormatter(final String datePattern) {
        return DateTimeFormatter.ofPattern(datePattern)
                .withZone(getZoneId());
    }

    private static ZoneId getZoneId() {
        return ZoneId.systemDefault();
    }
}
