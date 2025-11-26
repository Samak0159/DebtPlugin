package com.github.fligneul.debtplugin.debt.toolwindow;

import com.github.fligneul.debtplugin.debt.model.Priority;
import com.github.fligneul.debtplugin.debt.model.Risk;
import com.github.fligneul.debtplugin.debt.model.Status;
import com.github.fligneul.debtplugin.debt.model.Complexity;
import com.github.fligneul.debtplugin.debt.model.DebtItem;
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

import javax.swing.*;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class DebtToolWindow {
    private final Project project;
    private final DebtService debtService;
    private final DebtTableModel tableModel;
    private final JBTable table;

    public DebtToolWindow(Project project) {
        this.project = project;
        this.debtService = project.getService(DebtService.class);
        this.tableModel = new DebtTableModel(debtService);
        this.table = new JBTable(tableModel);

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
        actionCol.setCellRenderer(new DeleteButtonCell(row -> {
            DebtItem debtItem = tableModel.debtItems.get(row);
            debtService.remove(debtItem);
            updateTable();
        }));
        actionCol.setCellEditor(new DeleteButtonCell(row -> {
            DebtItem debtItem = tableModel.debtItems.get(row);
            debtService.remove(debtItem);
            updateTable();
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

        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = table.getSelectedRow();
                    if (row >= 0) {
                        String file = tableModel.debtItems.get(row).getFile();
                        int line = (Integer) table.getValueAt(row, 1);
                        VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(file);
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

    private void updateTable() {
        tableModel.clearAll();
        for (DebtItem item : debtService.all()) {
            tableModel.addDebtItem(item);
        }
    }
}
