package com.github.fligneul.debtplugin.debt.service;

import com.github.fligneul.debtplugin.debt.model.Complexity;
import com.github.fligneul.debtplugin.debt.model.DebtItem;
import com.github.fligneul.debtplugin.debt.model.Priority;
import com.github.fligneul.debtplugin.debt.model.Risk;
import com.github.fligneul.debtplugin.debt.model.Status;
import com.github.fligneul.debtplugin.debt.settings.DebtSettings;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service(Service.Level.PROJECT)
public final class DebtService {
    private final Project project;
    private final Gson gson;
    private final DebtSettings settings;
    private final List<DebtItem> debts = new ArrayList<>();
    private File debtFile;

    public DebtService(@NotNull Project project) {
        this.project = Objects.requireNonNull(project, "project");
        this.settings = project.getService(DebtSettings.class);
        this.gson = new GsonBuilder()
                .registerTypeAdapter(DebtItem.class, new DebtItemDeserializer())
                .create();
        this.debtFile = resolveDebtFile();
        loadDebts();
    }

    private File resolveDebtFile() {
        String basePath = project.getBasePath();
        String rel = settings.getState().getDebtFilePath();
        if (basePath == null) basePath = new File(".").getAbsolutePath();
        return new File(basePath, rel);
    }

    public synchronized void add(@NotNull DebtItem debtItem) {
        debts.add(Objects.requireNonNull(debtItem));
        saveDebts();
        refreshHighlighting();
    }

    public synchronized void remove(@NotNull DebtItem debtItem) {
        debts.remove(debtItem);
        saveDebts();
        refreshHighlighting();
    }

    public synchronized void update(@NotNull DebtItem oldDebtItem, @NotNull DebtItem newDebtItem) {
        int index = debts.indexOf(oldDebtItem);
        if (index != -1) {
            debts.set(index, newDebtItem);
            saveDebts();
            refreshHighlighting();
        }
    }

    @NotNull
    public synchronized List<DebtItem> all() {
        return new ArrayList<>(debts);
    }

    // Kotlin property accessor compatibility: debtService.debtFile
    @NotNull
    public File getDebtFile() {
        if (debtFile == null) {
            debtFile = resolveDebtFile();
        }
        return debtFile;
    }

    public synchronized void migrateUsername(@NotNull String oldUsername, @NotNull String newUsername) {
        if (oldUsername.isBlank() || oldUsername.equals(newUsername)) return;
        boolean changed = false;
        for (int i = 0; i < debts.size(); i++) {
            DebtItem d = debts.get(i);
            if (oldUsername.equals(d.getUsername())) {
                DebtItem updated = new DebtItem(
                        d.getFile(),
                        d.getLine(),
                        d.getTitle(),
                        d.getDescription(),
                        newUsername,
                        d.getWantedLevel(),
                        d.getComplexity(),
                        d.getStatus(),
                        d.getPriority(),
                        d.getRisk(),
                        d.getTargetVersion(),
                        d.getComment()
                );
                updated.setCurrentModule(d.getCurrentModule());
                debts.set(i, updated);
                changed = true;
            }
        }
        if (changed) {
            saveDebts();
            refreshHighlighting();
        }
    }

    private void loadDebts() {
        File f = getDebtFile();
        if (f.exists()) {
            try {
                String content = Files.readString(f.toPath(), StandardCharsets.UTF_8);
                Type type = new TypeToken<List<DebtItem>>() {
                }.getType();
                List<DebtItem> loaded = gson.fromJson(content, type);
                debts.clear();
                boolean changed = false;
                if (loaded != null) {
                    String basePath = project.getBasePath();
                    for (DebtItem d : loaded) {
                        String original = d.getFile();
                        String relativized = toProjectRelative(original, basePath);
                        if (!Objects.equals(original, relativized)) {
                            d.setFile(relativized);
                            changed = true;
                        }
                        debts.add(d);
                    }
                    if (changed) {
                        saveDebts();
                    }
                }
            } catch (IOException ignored) {
            }
        }
    }

    private void saveDebts() {
        File f = getDebtFile();
        try {
            String json = gson.toJson(debts);
            File parent = f.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            Files.writeString(f.toPath(), json, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    private void refreshHighlighting() {
        DaemonCodeAnalyzer.getInstance(project).restart();
    }

    private static String toProjectRelative(String anyPath, String basePath) {
        if (anyPath == null) return "";
        String norm = anyPath.replace('\\', '/');
        try {
            Path p = Paths.get(anyPath);
            if (basePath != null) {
                Path base = Paths.get(basePath).toAbsolutePath().normalize();
                Path abs = p.isAbsolute() ? p.toAbsolutePath().normalize() : base.resolve(p).normalize();
                if (abs.startsWith(base)) {
                    return base.relativize(abs).toString().replace('\\', '/');
                }
                return abs.toString().replace('\\', '/');
            } else {
                Path abs = p.isAbsolute() ? p.toAbsolutePath().normalize() : p.normalize();
                return abs.toString().replace('\\', '/');
            }
        } catch (Exception e) {
            return norm;
        }
    }

    private static final class DebtItemDeserializer implements JsonDeserializer<DebtItem> {
        @Override
        public DebtItem deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            JsonObject obj = json.getAsJsonObject();
            String file = getAsString(obj, "file", "");
            int line = getAsInt(obj, "line", 1);
            String title = getAsString(obj, "title", "");
            String description = getAsString(obj, "description", "");
            String username = getAsString(obj, "username", "");
            int wantedLevel = getAsInt(obj, "wantedLevel", 3);

            Complexity complexity = parseEnum(obj, "complexity", Complexity.Medium, Complexity.class);
            Status status = parseEnum(obj, "status", Status.Submitted, Status.class);
            Priority priority = parseEnum(obj, "priority", Priority.Medium, Priority.class);
            Risk risk = parseEnum(obj, "risk", Risk.Medium, Risk.class);

            String targetVersion = getAsString(obj, "targetVersion", "");
            String comment = getAsString(obj, "comment", "");

            DebtItem item = new DebtItem(
                    file,
                    line,
                    title,
                    description,
                    username,
                    wantedLevel,
                    complexity,
                    status,
                    priority,
                    risk,
                    targetVersion,
                    comment
            );
            // Optional, backward-compatible: prefer currentModule, fallback to legacy moduleParent
            String currentModule = getAsString(obj, "currentModule", null);
            if (currentModule == null || currentModule.isBlank()) {
                currentModule = getAsString(obj, "moduleParent", "");
            }
            if (currentModule != null) item.setCurrentModule(currentModule);
            return item;
        }

        private static String getAsString(JsonObject obj, String key, String def) {
            JsonElement e = obj.get(key);
            return e == null || e.isJsonNull() ? def : e.getAsString();
        }

        private static int getAsInt(JsonObject obj, String key, int def) {
            try {
                JsonElement e = obj.get(key);
                return e == null || e.isJsonNull() ? def : e.getAsInt();
            } catch (Exception ex) {
                return def;
            }
        }

        private static <E extends Enum<E>> E parseEnum(JsonObject obj, String key, E def, Class<E> enumType) {
            try {
                String s = getAsString(obj, key, null);
                if (s == null || s.isBlank()) return def;
                return Enum.valueOf(enumType, s);
            } catch (Exception ex) {
                return def;
            }
        }
    }
}
