package com.github.fligneul.debtplugin.debt.toolwindow;

import com.github.fligneul.debtplugin.debt.model.Priority;
import com.github.fligneul.debtplugin.debt.model.Risk;
import com.github.fligneul.debtplugin.debt.model.Status;
import com.github.fligneul.debtplugin.debt.model.Complexity;
import com.github.fligneul.debtplugin.debt.model.DebtItem;
import com.github.fligneul.debtplugin.debt.service.DebtService;

import javax.swing.table.DefaultTableModel;
import java.util.ArrayList;
import java.util.List;

public class DebtTableModel extends DefaultTableModel {
    public final List<DebtItem> debtItems = new ArrayList<>();
    private final DebtService debtService;

    public DebtTableModel(DebtService debtService) {
        this.debtService = debtService;
        addColumn("File"); // 0
        addColumn("Line"); // 1
        addColumn("Title"); // 2
        addColumn("Description"); // 3
        addColumn("User"); // 4
        addColumn("WantedLevel"); // 5
        addColumn("Complexity"); // 6
        addColumn("Status"); // 7
        addColumn("Priority"); // 8
        addColumn("Risk"); // 9
        addColumn("TargetVersion"); // 10
        addColumn("Comment"); // 11
        addColumn(""); // 12 action (delete)
    }

    @Override
    public boolean isCellEditable(int row, int column) {
        // Make the first two columns (File and Line) non-editable
        // Keep the User column (4) non-editable; Delete button column (12) must be editable for the button
        return (column >= 2) && (column != 4);
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return switch (columnIndex) {
            case 5 -> Integer.class; // WantedLevel column
            case 6 -> Complexity.class; // Complexity column
            case 7 -> Status.class; // Status column
            case 8 -> Priority.class; // Priority column
            case 9 -> Risk.class; // Risk column
            case 12 -> Object.class; // Action column (for button)
            default -> super.getColumnClass(columnIndex);
        };
    }

    @Override
    public void setValueAt(Object aValue, int row, int column) {
        DebtItem oldDebtItem = debtItems.get(row);
        DebtItem updatedDebtItem;
        switch (column) {
            case 2 -> updatedDebtItem = new DebtItem(
                    oldDebtItem.getFile(), oldDebtItem.getLine(),
                    (String) aValue, // title
                    oldDebtItem.getDescription(),
                    oldDebtItem.getUsername(),
                    oldDebtItem.getWantedLevel(),
                    oldDebtItem.getComplexity(),
                    oldDebtItem.getStatus(),
                    oldDebtItem.getPriority(),
                    oldDebtItem.getRisk(),
                    oldDebtItem.getTargetVersion(),
                    oldDebtItem.getComment()
            );
            case 3 -> updatedDebtItem = new DebtItem(
                    oldDebtItem.getFile(), oldDebtItem.getLine(),
                    oldDebtItem.getTitle(),
                    (String) aValue, // description
                    oldDebtItem.getUsername(),
                    oldDebtItem.getWantedLevel(),
                    oldDebtItem.getComplexity(),
                    oldDebtItem.getStatus(),
                    oldDebtItem.getPriority(),
                    oldDebtItem.getRisk(),
                    oldDebtItem.getTargetVersion(),
                    oldDebtItem.getComment()
            );
            case 5 -> {
                int asInt;
                if (aValue instanceof Number n) asInt = n.intValue();
                else if (aValue instanceof String s) {
                    try { asInt = Integer.parseInt(s); } catch (NumberFormatException ex) { asInt = oldDebtItem.getWantedLevel(); }
                } else asInt = oldDebtItem.getWantedLevel();
                updatedDebtItem = new DebtItem(
                        oldDebtItem.getFile(), oldDebtItem.getLine(),
                        oldDebtItem.getTitle(),
                        oldDebtItem.getDescription(),
                        oldDebtItem.getUsername(),
                        asInt,
                        oldDebtItem.getComplexity(),
                        oldDebtItem.getStatus(),
                        oldDebtItem.getPriority(),
                        oldDebtItem.getRisk(),
                        oldDebtItem.getTargetVersion(),
                        oldDebtItem.getComment()
                );
            }
            case 6 -> updatedDebtItem = new DebtItem(
                    oldDebtItem.getFile(), oldDebtItem.getLine(),
                    oldDebtItem.getTitle(),
                    oldDebtItem.getDescription(),
                    oldDebtItem.getUsername(),
                    oldDebtItem.getWantedLevel(),
                    (Complexity) aValue,
                    oldDebtItem.getStatus(),
                    oldDebtItem.getPriority(),
                    oldDebtItem.getRisk(),
                    oldDebtItem.getTargetVersion(),
                    oldDebtItem.getComment()
            );
            case 7 -> updatedDebtItem = new DebtItem(
                    oldDebtItem.getFile(), oldDebtItem.getLine(),
                    oldDebtItem.getTitle(),
                    oldDebtItem.getDescription(),
                    oldDebtItem.getUsername(),
                    oldDebtItem.getWantedLevel(),
                    oldDebtItem.getComplexity(),
                    (Status) aValue,
                    oldDebtItem.getPriority(),
                    oldDebtItem.getRisk(),
                    oldDebtItem.getTargetVersion(),
                    oldDebtItem.getComment()
            );
            case 8 -> updatedDebtItem = new DebtItem(
                    oldDebtItem.getFile(), oldDebtItem.getLine(),
                    oldDebtItem.getTitle(),
                    oldDebtItem.getDescription(),
                    oldDebtItem.getUsername(),
                    oldDebtItem.getWantedLevel(),
                    oldDebtItem.getComplexity(),
                    oldDebtItem.getStatus(),
                    (Priority) aValue,
                    oldDebtItem.getRisk(),
                    oldDebtItem.getTargetVersion(),
                    oldDebtItem.getComment()
            );
            case 9 -> updatedDebtItem = new DebtItem(
                    oldDebtItem.getFile(), oldDebtItem.getLine(),
                    oldDebtItem.getTitle(),
                    oldDebtItem.getDescription(),
                    oldDebtItem.getUsername(),
                    oldDebtItem.getWantedLevel(),
                    oldDebtItem.getComplexity(),
                    oldDebtItem.getStatus(),
                    oldDebtItem.getPriority(),
                    (Risk) aValue,
                    oldDebtItem.getTargetVersion(),
                    oldDebtItem.getComment()
            );
            case 10 -> updatedDebtItem = new DebtItem(
                    oldDebtItem.getFile(), oldDebtItem.getLine(),
                    oldDebtItem.getTitle(),
                    oldDebtItem.getDescription(),
                    oldDebtItem.getUsername(),
                    oldDebtItem.getWantedLevel(),
                    oldDebtItem.getComplexity(),
                    oldDebtItem.getStatus(),
                    oldDebtItem.getPriority(),
                    oldDebtItem.getRisk(),
                    (String) aValue, // targetVersion
                    oldDebtItem.getComment()
            );
            case 11 -> updatedDebtItem = new DebtItem(
                    oldDebtItem.getFile(), oldDebtItem.getLine(),
                    oldDebtItem.getTitle(),
                    oldDebtItem.getDescription(),
                    oldDebtItem.getUsername(),
                    oldDebtItem.getWantedLevel(),
                    oldDebtItem.getComplexity(),
                    oldDebtItem.getStatus(),
                    oldDebtItem.getPriority(),
                    oldDebtItem.getRisk(),
                    oldDebtItem.getTargetVersion(),
                    (String) aValue // comment
            );
            default -> updatedDebtItem = oldDebtItem;
        }

        if (!updatedDebtItem.equals(oldDebtItem)) {
            debtItems.set(row, updatedDebtItem);
            debtService.update(oldDebtItem, updatedDebtItem);
            super.setValueAt(aValue, row, column);
        }
    }

    public void addDebtItem(DebtItem debtItem) {
        debtItems.add(debtItem);
        String displayedFile = debtItem.getFile().replace('\\', '/');
        int lastSlash = displayedFile.lastIndexOf('/');
        if (lastSlash >= 0) displayedFile = displayedFile.substring(lastSlash + 1);
        addRow(new Object[]{
                displayedFile,
                debtItem.getLine(),
                debtItem.getTitle(),
                debtItem.getDescription(),
                debtItem.getUsername(),
                debtItem.getWantedLevel(),
                debtItem.getComplexity(),
                debtItem.getStatus(),
                debtItem.getPriority(),
                debtItem.getRisk(),
                debtItem.getTargetVersion(),
                debtItem.getComment(),
                null
        });
    }

    public void clearAll() {
        debtItems.clear();
        setRowCount(0);
    }
}
