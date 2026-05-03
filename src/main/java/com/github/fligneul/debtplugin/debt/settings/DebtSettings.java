package com.github.fligneul.debtplugin.debt.settings;

import com.github.fligneul.debtplugin.debt.model.Field;
import com.github.fligneul.debtplugin.debt.toolwindow.chart.EClassifiers;
import com.github.fligneul.debtplugin.debt.toolwindow.chart.panel.EChart;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@State(
        name = "com.github.fligneul.debtplugin.debt.settings.DebtSettings",
        storages = {@Storage("debt.xml")}
)
@Service(Service.Level.PROJECT)
public final class DebtSettings implements PersistentStateComponent<DebtSettings.State> {

    public static final Topic<DebtSettingsListener> TOPIC = Topic.create("Debt Settings Changed", DebtSettingsListener.class);
    public static final String DEFAULT_DEBT_FILE_PATH = "dev/debt.json";
    public static final int DEFAULT_MAX_CHAR_TEXT_AREA = Integer.MAX_VALUE;
    private static final String DEFAULT_DATE_PATTERN = "yyyy.MM.dd";
    private static final int CHART_DISPLAY_LIMIT_VALUES_DEFAULT = 5;
    private static final EChart DEFAULT_CHART_TYPE = EChart.Pie;

    private static LinkedHashMap<String, Boolean> initCreationVisibility() {
        final LinkedHashMap<String, Boolean> map = new LinkedHashMap<>();
        for (final Field field : Field.values()) {
            map.put(field.name(), field.isDefaultVisibilty());
        }

        return map;
    }

    public static final class State {
        public String username = "";
        // Optional per-repository override for JSON path. Key: absolute repo root; Value: absolute path or path relative to repo root.
        public Map<String, String> repoDebtPaths = new HashMap<>();
        // Column visibility by column display name ("(Actions)" for empty header). true => visible, false => hidden
        public Map<String, Boolean> columnVisibility = new LinkedHashMap<>();

        public String datePattern = DEFAULT_DATE_PATTERN;
        // Field to display on the creation dialogPane
        public Map<String, Boolean> creationVisibility = new LinkedHashMap<>(initCreationVisibility());
        public Integer maxCharTextArea = DEFAULT_MAX_CHAR_TEXT_AREA;
        public EChart chartType = DEFAULT_CHART_TYPE;
        public EClassifiers chartClassifier = EClassifiers.DEFAULT;
        public Integer chartDisplayLimitValues = CHART_DISPLAY_LIMIT_VALUES_DEFAULT;

        public State() {
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public Map<String, String> getRepoDebtPaths() {
            return repoDebtPaths;
        }

        public void setRepoDebtPaths(Map<String, String> repoDebtPaths) {
            this.repoDebtPaths = repoDebtPaths;
        }

        public Map<String, Boolean> getColumnVisibility() {
            return columnVisibility;
        }

        public void setColumnVisibility(Map<String, Boolean> columnVisibility) {
            this.columnVisibility = columnVisibility;
        }

        public @NotNull Map<String, Boolean> getCreationVisibility() {
            return creationVisibility;
        }

        public void setCreationVisibility(final Map<String, Boolean> creationVisibility) {
            this.creationVisibility = creationVisibility;
        }

        public String getDebtFilePath(Project project) {
            final String basePath = project.getBasePath();

            return Optional.ofNullable(getRepoDebtPaths().get(basePath))
                    .or(() -> Optional.ofNullable(basePath)
                            .map(base -> base.replace("/", "\\"))
                            .map(windowsBasePath -> getRepoDebtPaths().get(windowsBasePath)))
                    .orElseGet(() -> {
                        final String path = Optional.ofNullable(basePath)
                                .orElse("");
                        return Paths.get(path)
                                .resolve(DEFAULT_DEBT_FILE_PATH)
                                .normalize()
                                .toString();
                    });
        }

        public String getDatePattern() {
            return datePattern;
        }

        public void setDatePattern(final String datePattern) {
            this.datePattern = datePattern;
        }

        public void setMaxCharTextArea(int maxCharTextArea) {
            this.maxCharTextArea = maxCharTextArea;
        }

        public int getMaxCharTextArea() {
            return this.maxCharTextArea;
        }

        public EChart getChartType() {
            return chartType;
        }

        public void setChartType(EChart chartType) {
            this.chartType = chartType;
        }

        public int getChartDisplayLimitValues() {
            return chartDisplayLimitValues;
        }

        public void setChartDisplayLimitValues(int chartDisplayLimitValues) {
            this.chartDisplayLimitValues = chartDisplayLimitValues;
        }

        public EClassifiers getChartClassifier() {
            return chartClassifier;
        }

        public void setChartClassifier(EClassifiers chartClassifier) {
            this.chartClassifier = chartClassifier;
        }
    }

    private State myState = new State();

    @Override
    public @NotNull State getState() {
        if (myState.username == null || myState.username.isBlank()) {
            myState.username = UUID.randomUUID().toString();
        }
        if (myState.repoDebtPaths == null) {
            myState.repoDebtPaths = new HashMap<>();
        }
        if (myState.columnVisibility == null) {
            myState.columnVisibility = new LinkedHashMap<>();
        }
        if (myState.datePattern == null) {
            myState.datePattern = DEFAULT_DATE_PATTERN;
        }
        if (myState.creationVisibility == null) {
            myState.creationVisibility = initCreationVisibility();
        }
        if (myState.maxCharTextArea == null) {
            myState.maxCharTextArea = DEFAULT_MAX_CHAR_TEXT_AREA;
        }

        if (myState.chartType == null) {
            myState.chartType = DEFAULT_CHART_TYPE;
        }

        if (myState.chartDisplayLimitValues == null) {
            myState.chartDisplayLimitValues = CHART_DISPLAY_LIMIT_VALUES_DEFAULT;
        }

        if (myState.chartClassifier == null) {
            myState.chartClassifier = EClassifiers.DEFAULT;
        }
        return myState;
    }

    @Override
    public void loadState(@NotNull State state) {
        this.myState = state;
        if (myState.username == null || myState.username.isBlank()) {
            myState.username = UUID.randomUUID().toString();
        }
        if (myState.repoDebtPaths == null) myState.repoDebtPaths = new HashMap<>();
        if (myState.columnVisibility == null) myState.columnVisibility = new LinkedHashMap<>();
        if (myState.datePattern == null) myState.datePattern = DEFAULT_DATE_PATTERN;
        if (myState.creationVisibility == null) myState.creationVisibility = initCreationVisibility();
        if (myState.maxCharTextArea == null) myState.maxCharTextArea = DEFAULT_MAX_CHAR_TEXT_AREA;
        if (myState.chartType == null) myState.chartType = DEFAULT_CHART_TYPE;
        if (myState.chartDisplayLimitValues == null) myState.chartDisplayLimitValues = CHART_DISPLAY_LIMIT_VALUES_DEFAULT;
        if (myState.chartClassifier == null) myState.chartClassifier = EClassifiers.DEFAULT;
    }

    public String getOrInitUsername() {
        if (myState.username == null || myState.username.isBlank()) {
            myState.username = UUID.randomUUID().toString();
        }
        return myState.username;
    }
}
