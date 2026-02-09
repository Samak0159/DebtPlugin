package com.github.fligneul.debtplugin.debt.toolwindow;

import com.intellij.icons.AllIcons;

import javax.swing.*;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.util.function.IntConsumer;

/**
 * Table cell that renders two small icon buttons: Edit (pencil) and Delete (trash).
 * Uses the same component for both renderer and editor for simplicity.
 */
public class ActionButtonsCell extends AbstractCellEditor implements TableCellRenderer, TableCellEditor {
    private final JPanel panel;
    private final JButton editButton;
    private final JButton deleteButton;
    private final IntConsumer onEdit;
    private final IntConsumer onDelete;
    private int row = -1;

    public ActionButtonsCell(IntConsumer onEdit, IntConsumer onDelete) {
        this.onEdit = onEdit;
        this.onDelete = onDelete;
        panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 2));
        panel.setOpaque(true);

        editButton = new JButton(AllIcons.Actions.Edit);
        editButton.setOpaque(true);
        editButton.setBorder(BorderFactory.createEmptyBorder());
        editButton.setMargin(new Insets(0, 0, 0, 0));
        editButton.setFocusable(false);
        editButton.setContentAreaFilled(false);
        editButton.setPreferredSize(new Dimension(16, 16));
        editButton.setMaximumSize(new Dimension(18, 18));
        editButton.setToolTipText("Edit");
        editButton.addActionListener(e -> {
            fireEditingStopped();
            if (onEdit != null && row >= 0) onEdit.accept(row);
        });

        deleteButton = new JButton(AllIcons.Actions.DeleteTag);
        deleteButton.setOpaque(true);
        deleteButton.setBorder(BorderFactory.createEmptyBorder());
        deleteButton.setMargin(new Insets(0, 0, 0, 0));
        deleteButton.setFocusable(false);
        deleteButton.setContentAreaFilled(false);
        deleteButton.setPreferredSize(new Dimension(16, 16));
        deleteButton.setMaximumSize(new Dimension(18, 18));
        deleteButton.setToolTipText("Delete");
        deleteButton.addActionListener(e -> {
            fireEditingStopped();
            if (onDelete != null && row >= 0) onDelete.accept(row);
        });

        panel.add(editButton);
        panel.add(Box.createHorizontalStrut(4));
        panel.add(deleteButton);
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        if (isSelected) {
            panel.setBackground(table.getSelectionBackground());
        } else {
            panel.setBackground(table.getBackground());
        }
        return panel;
    }

    @Override
    public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
        this.row = row;
        return panel;
    }

    @Override
    public Object getCellEditorValue() {
        return null;
    }
}
