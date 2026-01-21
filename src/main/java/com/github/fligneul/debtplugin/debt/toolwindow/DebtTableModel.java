package com.github.fligneul.debtplugin.debt.toolwindow;

import com.github.fligneul.debtplugin.debt.model.Complexity;
import com.github.fligneul.debtplugin.debt.model.DebtItem;
import com.github.fligneul.debtplugin.debt.model.Priority;
import com.github.fligneul.debtplugin.debt.model.Risk;
import com.github.fligneul.debtplugin.debt.model.Status;
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
            case 2 -> updatedDebtItem = oldDebtItem.toBuilder()
                    .withTitle((String) aValue)
                    .build();
            case 3 -> updatedDebtItem =
                    oldDebtItem.toBuilder()
                            .withDescription((String) aValue)
                            .build();
            case 5 -> {
                int asInt;
                if (aValue instanceof Number n) asInt = n.intValue();
                else if (aValue instanceof String s) {
                    try {
                        asInt = Integer.parseInt(s);
                    } catch (NumberFormatException ex) {
                        asInt = oldDebtItem.getWantedLevel();
                    }
                } else asInt = oldDebtItem.getWantedLevel();
                // Clamp to [1..5]
                int clamped = Math.max(1, Math.min(5, asInt));
                updatedDebtItem = oldDebtItem.toBuilder()
                        .withWantedLevel(clamped)
                        .build();
                // Also reflect the clamped value in the table model
                aValue = clamped;
            }
            case 6 -> updatedDebtItem = oldDebtItem.toBuilder()
                    .withComplexity((Complexity) aValue)
                    .build();
            case 7 -> updatedDebtItem = oldDebtItem.toBuilder()
                    .withStatus((Status) aValue)
                    .build();
            case 8 -> updatedDebtItem = oldDebtItem.toBuilder()
                    .withPriority((Priority) aValue)
                    .build();
            case 9 -> updatedDebtItem = oldDebtItem.toBuilder()
                    .withRisk((Risk) aValue)
                    .build();
            case 10 -> updatedDebtItem = oldDebtItem.toBuilder()
                    .withTargetVersion((String) aValue)
                    .build();
            case 11 -> updatedDebtItem = oldDebtItem.toBuilder()
                    .withComment((String) aValue)
                    .build();
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
