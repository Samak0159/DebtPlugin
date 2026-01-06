package com.github.fligneul.debtplugin.debt.settings;

import com.github.fligneul.debtplugin.debt.service.DebtService;
import com.github.fligneul.debtplugin.debt.service.RepositoriesService;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class DebtSettingsConfigurable implements Configurable {
    private final Project project;
    private final DebtSettings settings;
    private final JBTextField usernameField = new JBTextField();

    // Table model: Column 0 = Repository (name), not editable; Column 1 = JSON Path (editable)
    private final DefaultTableModel repoTableModel = new DefaultTableModel(new Object[]{"Repository", "JSON Path"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return column == 1; // only path column editable
        }
    };
    private final JTable repoTable = new JTable(repoTableModel);

    // Keep track of repo root for each table row
    private final List<String> tableRepoRoots = new LinkedList<>();

    public DebtSettingsConfigurable(Project project) {
        this.project = project;
        this.settings = project.getService(DebtSettings.class);
    }

    @Override
    public @Nls(capitalization = Nls.Capitalization.Title) String getDisplayName() {
        return "Debt Plugin";
    }

    @Override
    public @Nullable JComponent createComponent() {
        usernameField.setText(settings.getOrInitUsername());

        // Configure table appearance
        repoTable.setFillsViewportHeight(true);
        repoTable.setRowSelectionAllowed(false);

        rebuildRepoTable();

        JScrollPane repoTableScroll = new JScrollPane(repoTable);

        return FormBuilder.createFormBuilder()
                .addLabeledComponent("Username:", usernameField)
                .addLabeledComponent("Repository", repoTableScroll)
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

    @Override
    public boolean isModified() {
        boolean basic = !Objects.equals(usernameField.getText(), settings.getState().getUsername());
        if (basic) return true;
        Map<String, String> current = settings.getState().getRepoDebtPaths();
        if (current == null) current = new LinkedHashMap<>();
        Map<String, String> ui = collectUiRepoPaths();
        return !Objects.equals(ui, current);
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

        project.getMessageBus().syncPublisher(DebtSettings.TOPIC).settingsChanged(settings.getState());
    }

    @Override
    public void reset() {
        var reposService = project.getService(RepositoriesService.class);
        reposService.refreshFromMisc();

        usernameField.setText(settings.getOrInitUsername());
        rebuildRepoTable();
    }

}
