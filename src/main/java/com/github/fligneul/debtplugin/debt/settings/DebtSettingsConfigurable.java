package com.github.fligneul.debtplugin.debt.settings;

import com.github.fligneul.debtplugin.debt.model.Field;
import com.github.fligneul.debtplugin.debt.service.ColumnService;
import com.github.fligneul.debtplugin.debt.service.DebtService;
import com.github.fligneul.debtplugin.debt.service.RepositoriesService;
import com.github.fligneul.debtplugin.debt.toolwindow.chart.EClassifiers;
import com.github.fligneul.debtplugin.debt.toolwindow.chart.panel.EChart;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

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
    private final JBTextField datePatternField;
    private final JBTextField maxCharField;

    // Chart
    private final JBTextField chartLimit = new JBTextField(5);
    private final Map<EChart, JRadioButton> chartTypeButtons = new LinkedHashMap<>();
    private final Map<EClassifiers, JRadioButton> classifierButtons = new LinkedHashMap<>();

    public DebtSettingsConfigurable(Project project) {
        this.project = project;
        this.settings = project.getService(DebtSettings.class);
        this.columnService = project.getService(ColumnService.class);

        datePatternField = new JBTextField(settings.getState().getDatePattern());
        maxCharField = new JBTextField(String.valueOf(settings.getState().getMaxCharTextArea()));
    }

    @Override
    public @Nls(capitalization = Nls.Capitalization.Title) String getDisplayName() {
        return "Debt Plugin";
    }

    @Override
    public @Nullable JComponent createComponent() {
        usernameField.setText(settings.getOrInitUsername());

        // Configure table appearance
        repoTable.setRowSelectionAllowed(false);

        rebuildRepoTable();

        // Build columns panel
        columnsPanel.setLayout(new BoxLayout(columnsPanel, BoxLayout.Y_AXIS));
        rebuildColumnsPanelFromService();

        final JBScrollPane repoTableScroll = new JBScrollPane(repoTable);

        creationPanel.setLayout(new BoxLayout(creationPanel, BoxLayout.Y_AXIS));
        buildCreateFieldPanelFromService();

        final JPanel chartTypePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        chartTypePanel.add(new JLabel("Chart type:"));
        final ButtonGroup chartTypeButtonGroup = new ButtonGroup();
        chartTypeButtons.clear();
        Stream.of(EChart.values()).forEach(chart -> {
            final JRadioButton radioButton = new JRadioButton(chart.name(), chart == settings.getState().getChartType());
            chartTypeButtonGroup.add(radioButton);
            chartTypePanel.add(radioButton);
            chartTypeButtons.put(chart, radioButton);
        });

        final JPanel chartLimitPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        chartLimitPanel.add(new JLabel("Chart Limit:"));
        chartLimitPanel.add(chartLimit);
        chartLimit.setText(String.valueOf(settings.getState().getChartDisplayLimitValues()));

        final JPanel chartClassifierPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        chartClassifierPanel.add(new JLabel("Chart Classifier:"));
        final ButtonGroup classifierButtonGroup = new ButtonGroup();
        classifierButtons.clear();
        Stream.of(EClassifiers.values()).forEach(classifier -> {
            final JRadioButton radioButton = new JRadioButton(classifier.name(), classifier == settings.getState().getChartClassifier());
            classifierButtonGroup.add(radioButton);
            chartClassifierPanel.add(radioButton);
            classifierButtons.put(classifier, radioButton);
        });


        var chartContainer = new JPanel(new BorderLayout());
        chartContainer.add(chartTypePanel, BorderLayout.NORTH);
        chartContainer.add(chartLimitPanel, BorderLayout.CENTER);
        chartContainer.add(chartClassifierPanel, BorderLayout.SOUTH);

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
                .addLabeledComponent("Max char table's textArea :", maxCharField)
                .addSeparator()
                .addLabeledComponent("Chart :", chartContainer)
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
            checkBox.setSelected(settings.getState().getCreationVisibility().getOrDefault(name, field.isDefaultVisibilty()));

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
        if (!maxCharField.getText().isEmpty()) {
            basic |= !Objects.equals(Integer.parseInt(maxCharField.getText()), settings.getState().getMaxCharTextArea());
        }
        if (basic) return true;
        Map<String, String> current = settings.getState().getRepoDebtPaths();
        Map<String, String> ui = collectUiRepoPaths();
        if (!Objects.equals(ui, current)) return true;

        Map<String, Boolean> columnsVisibilities = settings.getState().getColumnVisibility();
        Map<String, Boolean> uiCols = collectUiColumnVisibility();
        if (!Objects.equals(uiCols, columnsVisibilities)) return true;

        Map<String, Boolean> creationVisibilities = settings.getState().getCreationVisibility();
        Map<String, Boolean> currentSelection = collectCreationFieldsVisibility();
        if (!Objects.equals(currentSelection, creationVisibilities)) return true;

        if (settings.getState().getChartType() != getSelectedChartType()) return true;

        if (settings.getState().getChartDisplayLimitValues() != Integer.parseInt(chartLimit.getText())) return true;

        return settings.getState().getChartClassifier() != getSelectedClassifier();
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

        if (maxCharField.getText().isEmpty()) {
            maxCharField.setText(String.valueOf(DebtSettings.DEFAULT_MAX_CHAR_TEXT_AREA));
            settings.getState().setMaxCharTextArea(DebtSettings.DEFAULT_MAX_CHAR_TEXT_AREA);
        } else {
            settings.getState().setMaxCharTextArea(Integer.parseInt(maxCharField.getText()));
        }

        settings.getState().setChartType(getSelectedChartType());
        settings.getState().setChartDisplayLimitValues(Integer.parseInt(chartLimit.getText()));
        settings.getState().setChartClassifier(getSelectedClassifier());

        // Notify listeners
        project.getMessageBus().syncPublisher(DebtSettings.TOPIC).settingsChanged(settings.getState());
    }

    private EChart getSelectedChartType() {
        return chartTypeButtons.entrySet()
                .stream()
                .filter(entry -> entry.getValue().isSelected())
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(EChart.Bar);
    }

    private EClassifiers getSelectedClassifier() {
        return classifierButtons.entrySet()
                .stream()
                .filter(entry -> entry.getValue().isSelected())
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(EClassifiers.DEFAULT);
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

        chartTypeButtons.forEach((type, button) -> button.setSelected(type == settings.getState().getChartType()));
        classifierButtons.forEach((classifier, button) -> button.setSelected(classifier == settings.getState().getChartClassifier()));
        chartLimit.setText(String.valueOf(settings.getState().getChartDisplayLimitValues()));

        columnsPanel.revalidate();
        columnsPanel.repaint();
    }

}
