package com.github.fligneul.debtplugin.debt.toolwindow;

import com.intellij.icons.AllIcons;

import javax.swing.*;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.util.function.IntConsumer;

public class DeleteButtonCell extends AbstractCellEditor implements TableCellRenderer, TableCellEditor {
    private final IntConsumer onClick;
    private final JButton button = new JButton(AllIcons.General.Delete);
    private int row = 0;

    public DeleteButtonCell(IntConsumer onClick) {
        this.onClick = onClick;
        button.setOpaque(true);
        button.addActionListener(e -> {
            fireEditingStopped();
            if (this.onClick != null) this.onClick.accept(row);
        });
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        return button;
    }

    @Override
    public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
        this.row = row;
        return button;
    }

    @Override
    public Object getCellEditorValue() {
        return null; // Button doesn't have a value
    }
}
