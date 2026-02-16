package com.github.fligneul.debtplugin.debt.settings;

import com.github.fligneul.debtplugin.debt.model.Field;
import com.github.fligneul.debtplugin.debt.service.ColumnService;
import com.github.fligneul.debtplugin.debt.service.DebtService;
import com.github.fligneul.debtplugin.debt.service.RepositoriesService;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.table.DefaultTableModel;
import java.awt.FlowLayout;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class DebtSettingsConfigurable implements Configurable {
    private final Project project;
    private final DebtSettings settings;
    private final ColumnService columnService;
    private final JBTextField usernameField = new JBTextField();

    // Table model: Column 0 = Repository (name), not editable; Column 1 = JSON Path (editable)
    private final DefaultTableModel repoTableModel = new DefaultTableModel(new Object[]{"Repository", "JSON Path"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return column == 1; // only path column editable
        }
    };
    private final JBTable repoTable = new JBTable(repoTableModel);

    // Keep track of repo root for each table row
    private final List<String> tableRepoRoots = new LinkedList<>();

    // Columns UI
    private final JPanel columnsPanel = new JPanel();
    private final Map<String, JCheckBox> columnChecks = new LinkedHashMap<>(); // name -> checkbox
    private final JPanel creationPanel = new JPanel();
    private final Map<String, JCheckBox> creationChecks = new LinkedHashMap<>(); // name -> checkbox
    private JBTextField datePatternField;

    public DebtSettingsConfigurable(Project project) {
        this.project = project;
        this.settings = project.getService(DebtSettings.class);
        this.columnService = project.getService(ColumnService.class);
    }

    @Override
    public @Nls(capitalization = Nls.Capitalization.Title) String getDisplayName() {
        return "Debt Plugin";
    }

    @Override
    public @Nullable JComponent createComponent() {
        usernameField.setText(settings.getOrInitUsername());
        datePatternField = new JBTextField(settings.getState().getDatePattern());

        // Configure table appearance
        repoTable.setRowSelectionAllowed(false);

        rebuildRepoTable();

        // Build columns panel
        columnsPanel.setLayout(new BoxLayout(columnsPanel, BoxLayout.Y_AXIS));
        rebuildColumnsPanelFromService();

        final JBScrollPane repoTableScroll = new JBScrollPane(repoTable);

        creationPanel.setLayout(new BoxLayout(creationPanel, BoxLayout.Y_AXIS));
        buildCreateFieldPanelFromService();

        return FormBuilder.createFormBuilder()
                .addLabeledComponent("Username:", usernameField)
                .addSeparator()
                .addLabeledComponent("Repository", repoTableScroll)
                .addSeparator()
                .addLabeledComponent("Columns:", columnsPanel)
                .addSeparator()
                .addLabeledComponent("Creation fields:", creationPanel)
                .addSeparator()
                .addLabeledComponent("DatePattern:", datePatternField)
                .getPanel();
    }

    private void rebuildRepoTable() {
        repoTableModel.setRowCount(0);
        tableRepoRoots.clear();

        final DebtService debtService = project.getService(DebtService.class);

        debtService.getDebtsByRepository()
                .keySet()
                .forEach(model -> {
                    repoTableModel.addRow(new Object[]{model.getRepositoryName(), model.getJsonPath()});
                    tableRepoRoots.add(model.getRepositoryAbsolutePath());
                });
    }

    private Map<String, String> collectUiRepoPaths() {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < repoTableModel.getRowCount(); i++) {
            String root = tableRepoRoots.get(i);
            Object val = repoTableModel.getValueAt(i, 1);
            String text = val == null ? "" : String.valueOf(val).trim();
            map.put(root, text);
        }
        return map;
    }

    private void rebuildColumnsPanelFromService() {
        columnsPanel.removeAll();
        columnChecks.clear();

        JPanel currentRowPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        for (int i = 0; i < columnService.getColumns().size(); i++) {
            if (i % 7 == 0) {
                currentRowPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
                columnsPanel.add(currentRowPanel);
            }

            final ColumnService.Column column = columnService.getColumns().get(i);

            JCheckBox cb = new JCheckBox(column.getName(), computeInitialVisibleFor(column.getName()));
            columnChecks.put(column.getName(), cb);
            currentRowPanel.add(cb);
        }

        columnsPanel.revalidate();
        columnsPanel.repaint();
    }

    private void buildCreateFieldPanelFromService() {
        creationPanel.removeAll();
        creationChecks.clear();

        JPanel currentRowPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        for (int i = 0; i < Field.values().length; i++) {
            if (i % 7 == 0) {
                currentRowPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
                creationPanel.add(currentRowPanel);
            }

            final Field field = Field.values()[i];

            final String name = field.name();
            final JCheckBox checkBox = new JCheckBox(name, computeInitialVisibleFor(name));
            checkBox.setEnabled(!field.isMandatory());
            checkBox.setSelected(settings.getState().getCreationVisibility().getOrDefault(name,field.isDefaultVisibilty()));

            creationChecks.put(name, checkBox);
            currentRowPanel.add(checkBox);
        }

        creationPanel.revalidate();
        creationPanel.repaint();
    }

    private boolean computeInitialVisibleFor(String columnName) {
        Map<String, Boolean> vis = settings.getState().getColumnVisibility();
        if (vis == null || vis.isEmpty()) return true; // default all visible
        Boolean v = vis.get(columnName);
        return v == null ? true : v;
    }

    private Map<String, Boolean> collectUiColumnVisibility() {
        Map<String, Boolean> map = new LinkedHashMap<>();
        for (Map.Entry<String, JCheckBox> e : columnChecks.entrySet()) {
            map.put(e.getKey(), e.getValue().isSelected());
        }
        return map;
    }

    private Map<String, Boolean> collectCreationFieldsVisibility() {
        Map<String, Boolean> map = new LinkedHashMap<>();
        for (Map.Entry<String, JCheckBox> e : creationChecks.entrySet()) {
            map.put(e.getKey(), e.getValue().isSelected());
        }
        return map;
    }

    @Override
    public boolean isModified() {
        boolean basic = !Objects.equals(usernameField.getText(), settings.getState().getUsername());
        basic |= !Objects.equals(datePatternField.getText(), settings.getState().getDatePattern());
        if (basic) return true;
        Map<String, String> current = settings.getState().getRepoDebtPaths();
        Map<String, String> ui = collectUiRepoPaths();
        if (!Objects.equals(ui, current)) return true;

        Map<String, Boolean> columnsVisibilities = settings.getState().getColumnVisibility();
        Map<String, Boolean> uiCols = collectUiColumnVisibility();
        if (!Objects.equals(uiCols, columnsVisibilities)) return true;

        Map<String, Boolean> creationVisibilities = settings.getState().getCreationVisibility();
        Map<String, Boolean> currentSelection = collectCreationFieldsVisibility();
        return !Objects.equals(currentSelection, creationVisibilities);

    }

    @Override
    public void apply() {
        String oldUsername = settings.getState().getUsername();
        String newUsername = usernameField.getText().trim();

        // Persist per-repo paths from table
        Map<String, String> ui = collectUiRepoPaths();

        // Before persisting, rename/move existing JSON files when paths changed
        Map<String, String> prev = settings.getState().getRepoDebtPaths();
        if (prev == null) prev = new LinkedHashMap<>();
        // Use a copy to avoid accidental mutation
        Map<String, String> prevCopy = new LinkedHashMap<>(prev);
        Map<String, String> newCopy = new LinkedHashMap<>(ui);
        DebtService debtService = project.getService(DebtService.class);
        debtService.renameRepoDebtJsonIfPathChanged(prevCopy, newCopy);

        // Now persist per-repo overrides
        settings.getState().setRepoDebtPaths(new LinkedHashMap<>(ui));

        if (!newUsername.isBlank() && !Objects.equals(newUsername, oldUsername)) {
            debtService.migrateUsername(oldUsername, newUsername);
            settings.getState().setUsername(newUsername);
        }

        // Persist column visibility
        Map<String, Boolean> uiCols = collectUiColumnVisibility();
        settings.getState().setColumnVisibility(new LinkedHashMap<>(uiCols));

        settings.getState().setDatePattern(datePatternField.getText());

        settings.getState().setCreationVisibility(collectCreationFieldsVisibility());

        // Notify listeners
        project.getMessageBus().syncPublisher(DebtSettings.TOPIC).settingsChanged(settings.getState());
    }

    @Override
    public void reset() {
        var reposService = project.getService(RepositoriesService.class);
        reposService.refreshAndLoadDebts();

        usernameField.setText(settings.getOrInitUsername());
        rebuildRepoTable();
        rebuildColumnsPanelFromService();
        buildCreateFieldPanelFromService();
        // sync checkbox states from settings in case of differences
        Map<String, Boolean> vis = settings.getState().getColumnVisibility();
        if (vis == null) vis = new LinkedHashMap<>();
        for (Map.Entry<String, JCheckBox> e : columnChecks.entrySet()) {
            Boolean v = vis.get(e.getKey());
            e.setValue(e.getValue()); // keep map structure
            e.getValue().setSelected(v == null ? true : v);
        }
        columnsPanel.revalidate();
        columnsPanel.repaint();
    }

}
