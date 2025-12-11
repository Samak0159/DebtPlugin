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
import com.github.fligneul.debtplugin.debt.listener.DebtDocumentListener;
import com.github.fligneul.debtplugin.debt.listener.DebtVfsListener;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
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
    private static final Logger LOG = Logger.getInstance(DebtService.class);
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
        LOG.info("DebtService initialized. debtFile=" + getDebtFile().getAbsolutePath() +
                " basePath=" + project.getBasePath() +
                " relPath=" + settings.getState().getDebtFilePath());
        loadDebts();
        // Listen to document changes to keep debt line numbers in sync with file edits
        try {
            EditorFactory.getInstance().getEventMulticaster().addDocumentListener(new DebtDocumentListener(project), project);
            LOG.info("DebtService: Document listener registered to update debt line numbers on edits");
        } catch (Throwable t) {
            LOG.warn("Failed to register document listener: " + t.getMessage(), t);
        }
        // Listen to VFS rename/move to keep debt file paths in sync
        try {
            project.getMessageBus().connect(project).subscribe(VirtualFileManager.VFS_CHANGES, new DebtVfsListener(project));
            LOG.info("DebtService: VFS listener registered to update debt file paths on rename/move");
        } catch (Throwable t) {
            LOG.warn("Failed to register VFS listener: " + t.getMessage(), t);
        }
    }

    private File resolveDebtFile() {
        String basePath = project.getBasePath();
        String rel = settings.getState().getDebtFilePath();
        if (basePath == null) basePath = new File(".").getAbsolutePath();
        File f = new File(basePath, rel);
        if (LOG.isDebugEnabled()) {
            LOG.debug("resolveDebtFile basePath=" + basePath + " relPath=" + rel + " -> " + f.getAbsolutePath());
        }
        return f;
    }

    public synchronized void add(@NotNull DebtItem debtItem) {
        DebtItem di = Objects.requireNonNull(debtItem);
        debts.add(di);
        LOG.info("Added debt: file=" + di.getFile() + ":" + di.getLine() +
                " title=\"" + di.getTitle() + "\"" +
                " desc=\"" + di.getDescription() + "\"" +
                " user=" + di.getUsername() +
                " targetVersion=\"" + di.getTargetVersion() + "\"" +
                " comment=\"" + di.getComment() + "\"" +
                " wantedLevel=" + di.getWantedLevel() +
                " complexity=" + di.getComplexity() +
                " status=" + di.getStatus() +
                " priority=" + di.getPriority() +
                " risk=" + di.getRisk());
        saveDebts();
        LOG.info("Debts saved after add. total=" + debts.size());
        refreshHighlighting();
    }

    public synchronized void remove(@NotNull DebtItem debtItem) {
        boolean removed = debts.remove(debtItem);
        if (removed) {
            LOG.info("Removed debt: file=" + debtItem.getFile() + ":" + debtItem.getLine() +
                    " title=\"" + debtItem.getTitle() + "\"" +
                    " desc=\"" + debtItem.getDescription() + "\"" +
                    " user=" + debtItem.getUsername() +
                    " targetVersion=\"" + debtItem.getTargetVersion() + "\"" +
                    " comment=\"" + debtItem.getComment() + "\"");
            saveDebts();
            LOG.info("Debts saved after remove. total=" + debts.size());
            refreshHighlighting();
        } else {
            LOG.warn("Attempted to remove non-existing debt: file=" + debtItem.getFile() + ":" + debtItem.getLine() +
                    " title=\"" + debtItem.getTitle() + "\"");
        }
    }

    public synchronized void update(@NotNull DebtItem oldDebtItem, @NotNull DebtItem newDebtItem) {
        int index = debts.indexOf(oldDebtItem);
        if (index != -1) {
            debts.set(index, newDebtItem);
            LOG.info("Updated debt: file=" + newDebtItem.getFile() + ":" + newDebtItem.getLine() +
                    " title=\"" + oldDebtItem.getTitle() + "\" -> \"" + newDebtItem.getTitle() + "\"" +
                    " desc=\"" + oldDebtItem.getDescription() + "\" -> \"" + newDebtItem.getDescription() + "\"" +
                    " user=" + newDebtItem.getUsername() +
                    " targetVersion=\"" + oldDebtItem.getTargetVersion() + "\" -> \"" + newDebtItem.getTargetVersion() + "\"" +
                    " comment=\"" + oldDebtItem.getComment() + "\" -> \"" + newDebtItem.getComment() + "\"" +
                    " wantedLevel=" + oldDebtItem.getWantedLevel() + "->" + newDebtItem.getWantedLevel() +
                    " complexity=" + oldDebtItem.getComplexity() + "->" + newDebtItem.getComplexity() +
                    " status=" + oldDebtItem.getStatus() + "->" + newDebtItem.getStatus() +
                    " priority=" + oldDebtItem.getPriority() + "->" + newDebtItem.getPriority() +
                    " risk=" + oldDebtItem.getRisk() + "->" + newDebtItem.getRisk());
            saveDebts();
            LOG.info("Debts saved after update. total=" + debts.size());
            refreshHighlighting();
        } else {
            LOG.warn("Attempted to update non-existing debt: file=" + oldDebtItem.getFile() + ":" + oldDebtItem.getLine() +
                    " title=\"" + oldDebtItem.getTitle() + "\"");
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
            if (LOG.isDebugEnabled()) LOG.debug("debtFile was null, resolving now");
            debtFile = resolveDebtFile();
        }
        return debtFile;
    }

    public synchronized void migrateUsername(@NotNull String oldUsername, @NotNull String newUsername) {
        if (oldUsername.isBlank() || oldUsername.equals(newUsername)) return;
        LOG.info("Migrating username: from=" + oldUsername + " to=" + newUsername);
        int changedCount = 0;
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
                changedCount++;
            }
        }
        if (changedCount > 0) {
            saveDebts();
            LOG.info("Username migration complete. changedItems=" + changedCount);
            refreshHighlighting();
        } else {
            LOG.info("Username migration: no items to update.");
        }
    }

    private void loadDebts() {
        File f = getDebtFile();
        if (f.exists()) {
            LOG.info("Loading debts from " + f.getAbsolutePath());
            try {
                String content = Files.readString(f.toPath(), StandardCharsets.UTF_8);
                Type type = new TypeToken<List<DebtItem>>() { }.getType();
                List<DebtItem> loaded = gson.fromJson(content, type);
                debts.clear();
                boolean changed = false;
                int relativizedCount = 0;
                if (loaded != null) {
                    String basePath = project.getBasePath();
                    for (DebtItem d : loaded) {
                        String original = d.getFile();
                        String relativized = toProjectRelative(original, basePath);
                        if (!Objects.equals(original, relativized)) {
                            d.setFile(relativized);
                            changed = true;
                            relativizedCount++;
                            if (LOG.isDebugEnabled()) {
                                LOG.debug("Relativized path from '" + original + "' to '" + relativized + "'");
                            }
                        }
                        debts.add(d);
                    }
                    if (changed) {
                        saveDebts();
                    }
                    LOG.info("Loaded debts: count=" + debts.size() + ", relativizedPaths=" + relativizedCount);
                } else {
                    LOG.info("Loaded debts: count=0");
                }
            } catch (com.google.gson.JsonParseException jpe) {
                LOG.error("Failed to parse debts JSON. path=" + f.getAbsolutePath() + " message=" + jpe.getMessage(), jpe);
            } catch (IOException io) {
                LOG.warn("Failed to read debts file. path=" + f.getAbsolutePath() + " message=" + io.getMessage(), io);
            }
        } else {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Debts file does not exist yet: " + f.getAbsolutePath());
            }
        }
    }

    private void saveDebts() {
        File f = getDebtFile();
        LOG.info("Saving debts. count=" + debts.size() + " path=" + f.getAbsolutePath());
        try {
            String json = gson.toJson(debts);
            File parent = f.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            Files.writeString(f.toPath(), json, StandardCharsets.UTF_8);
            LOG.info("Saved debts. count=" + debts.size() + " path=" + f.getAbsolutePath());
        } catch (IOException io) {
            LOG.warn("Failed to write debts file. path=" + f.getAbsolutePath() + " message=" + io.getMessage(), io);
        }
    }

    private void refreshHighlighting() {
        if (LOG.isDebugEnabled()) LOG.debug("refreshHighlighting invoked");
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
