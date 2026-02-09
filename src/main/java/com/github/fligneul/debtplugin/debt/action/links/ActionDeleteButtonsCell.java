package com.github.fligneul.debtplugin.debt.action.links;

import com.intellij.icons.AllIcons;

import javax.swing.AbstractCellEditor;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Insets;
import java.util.function.IntConsumer;

/**
 * Table cell that renders two small icon buttons: Edit (pencil) and Delete (trash).
 * Uses the same component for both renderer and editor for simplicity.
 */
public class ActionDeleteButtonsCell extends AbstractCellEditor implements TableCellRenderer, TableCellEditor {
    private final JPanel panel;
    private int row = -1;

    public ActionDeleteButtonsCell(IntConsumer onDelete) {
        panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 2));
        panel.setOpaque(true);

        final JButton deleteButton = new JButton(AllIcons.Actions.DeleteTag);
        deleteButton.setOpaque(true);
        deleteButton.setBorder(BorderFactory.createEmptyBorder());
        deleteButton.setMargin(new Insets(0, 0, 0, 0));
        deleteButton.setFocusable(false);
        deleteButton.setContentAreaFilled(false);
        deleteButton.setPreferredSize(new Dimension(16, 16));
        deleteButton.setMaximumSize(new Dimension(18, 18));
        deleteButton.setToolTipText("Delete");
        deleteButton.addActionListener(event -> {
            fireEditingStopped();
            if (onDelete != null && row >= 0) onDelete.accept(row);
        });

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
