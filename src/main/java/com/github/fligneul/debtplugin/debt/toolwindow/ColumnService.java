package com.github.fligneul.debtplugin.debt.toolwindow;

import com.intellij.openapi.components.Service;
import org.jetbrains.annotations.NotNull;

import javax.swing.JTable;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Project-level service that keeps knowledge about table columns: their model index, name,
 * visibility and the original TableColumn instance so we can hide/show without losing editors/renderers.
 */
@Service(Service.Level.PROJECT)
public final class ColumnService {

    public static final String ACTIONS_NAME = "(Actions)"; // display key for the empty header column

    public static final class Column {
        private final int index; // model index
        private final String name; // display name ("(Actions)" for empty)
        private boolean visible = true;
        private TableColumn tableColumn; // original instance captured from JTable once

        public Column(int index, String name) {
            this.index = index;
            this.name = name == null || name.isBlank() ? ACTIONS_NAME : name;
        }

        public int getIndex() {
            return index;
        }

        public String getName() {
            return name;
        }

        public boolean isVisible() {
            return visible;
        }

        public void setVisible(boolean v) {
            this.visible = v;
        }

        public TableColumn getTableColumn() {
            return tableColumn;
        }

        public void setTableColumn(TableColumn tc) {
            this.tableColumn = tc;
        }
    }

    private final Map<Integer, Column> columnsByIndex = new LinkedHashMap<>();

    public ColumnService() {
        String[] names = new String[]{
                "File",         // 0
                "Line",         // 1
                "Title",        // 2
                "Description",  // 3
                "User",         // 4
                "WantedLevel",  // 5
                "Complexity",   // 6
                "Status",       // 7
                "Priority",     // 8
                "Risk",         // 9
                "TargetVersion",// 10
                "Comment",      // 11
                "Estimation",   // 12
                "Module",       // 13
                ""              // 14 actions (empty header)
        };

        for (int i = 0; i < names.length; i++) {
            String display = names[i].isBlank() ? ACTIONS_NAME : names[i];
            Column column = new Column(i, display);
            columnsByIndex.put(i, column);
        }
    }

    /**
     * Capture the original TableColumn instances from the JTable once.
     */
    public synchronized void attachTableColumns(@NotNull JTable table) {
        TableColumnModel cm = table.getColumnModel();
        for (int v = 0; v < cm.getColumnCount(); v++) {
            TableColumn tc = cm.getColumn(v);
            Column c = columnsByIndex.get(tc.getModelIndex());
            if (c != null && c.getTableColumn() == null) {
                c.setTableColumn(tc);
            }
        }
    }

    public synchronized List<Column> getColumns() {
        return new ArrayList<>(columnsByIndex.values());
    }

    public synchronized void setVisibilityByName(Map<String, Boolean> visByName) {
        if (visByName == null || visByName.isEmpty()) {
            // Treat empty as all visible
            for (Column c : columnsByIndex.values()) c.setVisible(true);
            return;
        }
        for (Column c : columnsByIndex.values()) {
            Boolean v = visByName.get(c.getName());
            c.setVisible(v == null ? true : v);
        }
    }

    public synchronized Map<String, Boolean> getVisibilityByName() {
        Map<String, Boolean> out = new LinkedHashMap<>();
        for (Column c : columnsByIndex.values()) out.put(c.getName(), c.isVisible());
        return out;
    }

    public synchronized List<Integer> getVisibleModelIndices() {
        List<Integer> out = new ArrayList<>();
        for (Column c : columnsByIndex.values()) if (c.isVisible()) out.add(c.getIndex());
        return out;
    }

    /**
     * Apply current visibility to the JTable using stored original TableColumn instances.
     * Keeps view order equal to model index order of visible columns.
     */
    public synchronized void applyTo(@NotNull JTable table) {
        try {
            Set<Integer> toShow = new LinkedHashSet<>(getVisibleModelIndices());
            // If none flagged visible, treat as all
            if (toShow.isEmpty()) {
                for (Column c : columnsByIndex.values()) toShow.add(c.getIndex());
            }

            // Remove columns not desired (iterate from end)
            TableColumnModel cm = table.getColumnModel();
            for (int v = cm.getColumnCount() - 1; v >= 0; v--) {
                TableColumn tc = cm.getColumn(v);
                int modelIdx = tc.getModelIndex();
                if (!toShow.contains(modelIdx)) {
                    cm.removeColumn(tc);
                }
            }

            // Add missing
            Set<Integer> visibleNow = new LinkedHashSet<>();
            for (int v = 0; v < cm.getColumnCount(); v++) visibleNow.add(cm.getColumn(v).getModelIndex());
            for (int modelIdx : toShow) {
                if (!visibleNow.contains(modelIdx)) {
                    Column c = columnsByIndex.get(modelIdx);
                    if (c != null && c.getTableColumn() != null) cm.addColumn(c.getTableColumn());
                }
            }

            // Reorder to model order
            List<Integer> order = new ArrayList<>(toShow);
            for (int target = 0; target < order.size(); target++) {
                int modelIdx = order.get(target);
                int currentView = table.convertColumnIndexToView(modelIdx);
                if (currentView != -1 && currentView != target) cm.moveColumn(currentView, target);
            }

            table.revalidate();
            table.repaint();
        } catch (Exception ignored) {
        }
    }

    /**
     * Update visibility from a selection of model indices (e.g., from the UI column selector).
     */
    public synchronized void setVisibleByModelIndexSelection(Set<Integer> selectedModelIndices) {
        boolean all = selectedModelIndices == null || selectedModelIndices.isEmpty();
        for (Column c : columnsByIndex.values()) {
            c.setVisible(all || selectedModelIndices.contains(c.getIndex()));
        }
    }
}
