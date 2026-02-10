package com.github.fligneul.debtplugin.debt.service;

import com.github.fligneul.debtplugin.debt.listener.DebtDocumentListener;
import com.github.fligneul.debtplugin.debt.listener.DebtVfsListener;
import com.github.fligneul.debtplugin.debt.model.Complexity;
import com.github.fligneul.debtplugin.debt.model.DebtItem;
import com.github.fligneul.debtplugin.debt.model.Priority;
import com.github.fligneul.debtplugin.debt.model.Relationship;
import com.github.fligneul.debtplugin.debt.model.Repository;
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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service(Service.Level.PROJECT)
public final class DebtService {
    public static final Topic<DebtServiceListener> TOPIC = Topic.create("Debt Service Changed", DebtServiceListener.class);

    private static final Logger LOG = Logger.getInstance(DebtService.class);

    private final Project project;
    private final Gson gson;
    private final DebtSettings settings;
    // Unified storage: key = repository, value = items in that repo
    private final Map<Repository, List<DebtItem>> debtsByRepository = new LinkedHashMap<>();

    public DebtService(@NotNull Project project) {
        this.project = Objects.requireNonNull(project, "project");
        this.settings = project.getService(DebtSettings.class);
        this.gson = new GsonBuilder()
                .registerTypeAdapter(DebtItem.class, new DebtItemDeserializer())
                .setPrettyPrinting()
                .create();

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

    private void ensureJsonFileExist() {
        if (debtsByRepository.isEmpty()) {
            return;
        }
        final Map<String, String> jsonPathByRepositoryAbsolutePath = Optional.ofNullable(settings.getState())
                .map(DebtSettings.State::getRepoDebtPaths)
                .orElseGet(LinkedHashMap::new);

        for (Repository repository : debtsByRepository.keySet()) {
            String jsonPath = jsonPathByRepositoryAbsolutePath.get(repository.getRepositoryAbsolutePath());
            String pathToShow;
            if (jsonPath == null || jsonPath.isBlank()) {
                // show only the default relative path
                pathToShow = DebtSettings.DEFAULT_DEBT_FILE_PATH;
            } else {
                pathToShow = toRelativeIfPossible(repository.getRepositoryAbsolutePath(), jsonPath);
            }

            ensureFileExists(repository.getRepositoryAbsolutePath(), pathToShow);
            repository.setJsonPath(pathToShow);
        }
    }

    private static String toRelativeIfPossible(String repoRoot, String value) {
        if (value == null) return "";
        String v = value.trim();
        if (v.isEmpty()) return "";
        try {
            Path root = Paths.get(repoRoot).toAbsolutePath().normalize();
            Path p = Paths.get(v);
            Path abs = p.isAbsolute() ? p.toAbsolutePath().normalize() : root.resolve(p).normalize();
            if (abs.startsWith(root)) {
                return root.relativize(abs).toString().replace('\\', '/');
            }
            return v.replace('\\', '/');
        } catch (Exception e) {
            return v.replace('\\', '/');
        }
    }

    public synchronized void add(@NotNull DebtItem debtItem, @NotNull final String repoRoot) {
        getDebtForRepositoryAbsolutePath(repoRoot)
                .map(Map.Entry::getValue)
                .ifPresent(debts -> debts.add(debtItem));

        LOG.info("Added debtItem: file=" + debtItem.getFile() + ":" + debtItem.getLine() +
                " title=\"" + debtItem.getTitle() + "\"" +
                " desc=\"" + debtItem.getDescription() + "\"" +
                " user=" + debtItem.getUsername() +
                " targetVersion=\"" + debtItem.getTargetVersion() + "\"" +
                " comment=\"" + debtItem.getComment() + "\"" +
                " wantedLevel=" + debtItem.getWantedLevel() +
                " complexity=" + debtItem.getComplexity() +
                " status=" + debtItem.getStatus() +
                " priority=" + debtItem.getPriority() +
                " risk=" + debtItem.getRisk());

        saveDebts(repoRoot);

        refreshHighlighting();
    }

    public Optional<Map.Entry<Repository, List<DebtItem>>> getDebtForRepositoryAbsolutePath(final @NotNull String repoRoot) {
        return debtsByRepository.entrySet()
                .stream()
                .filter(entry -> entry.getKey().getRepositoryAbsolutePath().equals(repoRoot))
                .findFirst();
    }

    public synchronized void remove(@NotNull DebtItem debtItem) {
        debtsByRepository.entrySet()
                .stream()
                .filter(entry -> entry.getValue().contains(debtItem))
                .findFirst()
                .ifPresentOrElse(entry -> {
                            entry.getValue().removeIf(currentDebtItem -> currentDebtItem.equals(debtItem));

                            LOG.info("Removed debt: file=" + debtItem.getFile() + ":" + debtItem.getLine() +
                                    " title=\"" + debtItem.getTitle() + "\"" +
                                    " desc=\"" + debtItem.getDescription() + "\"" +
                                    " user=" + debtItem.getUsername() +
                                    " targetVersion=\"" + debtItem.getTargetVersion() + "\"" +
                                    " comment=\"" + debtItem.getComment() + "\"");

                            saveDebts(entry.getKey().getRepositoryAbsolutePath());
                        },
                        () -> LOG.warn("Attempted to remove non-existing debt: file=" + debtItem.getFile() + ":" + debtItem.getLine() +
                                " title=\"" + debtItem.getTitle() + "\""));
    }

    public synchronized void update(@NotNull DebtItem oldDebtItem, @NotNull DebtItem newDebtItem) {
        debtsByRepository.entrySet()
                .stream()
                .filter(entry -> entry.getValue().contains(oldDebtItem))
                .findFirst()
                .ifPresentOrElse(entry -> update(entry, oldDebtItem, newDebtItem),
                        () -> LOG.warn("Attempted to update non-existing debt: file=" + oldDebtItem.getFile() + ":" + oldDebtItem.getLine() +
                                " title=\"" + oldDebtItem.getTitle() + "\""));
    }

    public void update(final Map.Entry<Repository, List<DebtItem>> entry, final @NotNull DebtItem oldDebtItem, final @NotNull DebtItem newDebtItem) {
        final List<DebtItem> debts = entry.getValue();

        final int currentIndex = debts.indexOf(oldDebtItem);
        debts.remove(currentIndex);

        debts.add(currentIndex, newDebtItem);

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

        refreshHighlighting();
    }

    @NotNull
    public synchronized List<DebtItem> all() {
        return debtsByRepository.values()
                .stream()
                .flatMap(Collection::stream)
                .toList();
    }

    public synchronized void migrateUsername(@NotNull String oldUsername, @NotNull String newUsername) {
        if (oldUsername.isBlank() || oldUsername.equals(newUsername)) return;

        LOG.info("Migrating username: from=" + oldUsername + " to=" + newUsername);

        int changedCount = 0;
        for (final List<DebtItem> debts : debtsByRepository.values()) {
            for (int i = 0; i < debts.size(); i++) {
                DebtItem debtItem = debts.get(i);
                if (oldUsername.equals(debtItem.getUsername())) {
                    final DebtItem updated = debtItem.toBuilder()
                            .withUsername(newUsername)
                            .build();

                    debts.set(i, updated);
                    changedCount++;
                }
            }
        }

        if (changedCount > 0) {
            saveDebts();
            refreshHighlighting();
            LOG.info("Username migration complete. changedItems=" + changedCount);
        } else {
            LOG.info("Username migration: no items to update.");
        }
    }

    public void loadDebts() {
        debtsByRepository.clear();
        List<Repository> repositories = getRepositories();
        String relPath = settings.getState().getDebtFilePath(project);
        for (Repository repository : repositories) {
            try {
                File jsonFile = resolveRepoDebtFile(repository.getRepositoryAbsolutePath(), relPath);
                if (!jsonFile.exists()) {
                    debtsByRepository.put(repository, new ArrayList<>());
                    continue;
                }
                LOG.info("Loading debts from repo file: " + jsonFile.getAbsolutePath());
                String content = Files.readString(jsonFile.toPath(), StandardCharsets.UTF_8);
                Type type = new TypeToken<List<DebtItem>>() {
                }.getType();
                List<DebtItem> loaded = gson.fromJson(content, type);
                if (loaded == null) continue;
                debtsByRepository.put(repository, new ArrayList<>(loaded));
                LOG.info("Loaded debts total=%s from repos=%s".formatted(loaded.size(), repositories.size()));
            } catch (Exception ex) {
                LOG.warn("Failed loading debts for repoRoot=" + repository + ": " + ex.getMessage(), ex);
            }
        }

        ensureJsonFileExist();
    }

    private void saveDebts(String repoRoot) {
        getDebtForRepositoryAbsolutePath(repoRoot)
                .ifPresent(this::saveDebts);
    }

    /**
     * Save debts grouped by repository, writing one JSON file per repo root using the configured relative path.
     * Items without repoRoot are saved into the legacy project-level file for backward compatibility.
     */
    private void saveDebts() {
        for (Map.Entry<Repository, List<DebtItem>> entry : debtsByRepository.entrySet()) {
            saveDebts(entry);
        }
    }

    private void saveDebts(final Map.Entry<Repository, List<DebtItem>> entry) {
        final Repository repository = entry.getKey();

        final File jsonAbsolutePathFile = new File(repository.getRepositoryAbsolutePath(), repository.getJsonPath());
        final Path jsonPath = jsonAbsolutePathFile.toPath();
        final String jsonAbsolutePathStr = jsonAbsolutePathFile.getAbsolutePath();

        final List<DebtItem> items = entry.getValue();

        final String json = gson.toJson(items);

        try {
            Files.writeString(jsonPath, json, StandardCharsets.UTF_8);

            LOG.info("Saved repo debts. repoRoot=%s count=%s path=%s".formatted(
                    repository.getRepositoryAbsolutePath(),
                    items.size(),
                    jsonAbsolutePathStr));
        } catch (IOException io) {
            LOG.warn("Failed to write repo debts. path=" + jsonAbsolutePathStr + " message=" + io.getMessage(), io);
        }
    }

    private File resolveRepoDebtFile(String repoRoot, String relPath) {
        try {
            Map<String, String> overrides = settings.getState().getRepoDebtPaths();
            String val = overrides != null ? overrides.get(repoRoot) : null;
            return resolveRepoDebtFileWithOverride(repoRoot, val, relPath);
        } catch (Exception e) {
            return new File(repoRoot, relPath);
        }
    }

    private static File resolveRepoDebtFileWithOverride(String repoRoot, String overrideOrBlank, String defaultRelPath) {
        try {
            if (overrideOrBlank == null || overrideOrBlank.isBlank()) {
                return new File(repoRoot, defaultRelPath);
            }
            File file = new File(overrideOrBlank);
            return file.isAbsolute() ? file : new File(repoRoot, overrideOrBlank);
        } catch (Exception e) {
            return new File(repoRoot, defaultRelPath);
        }
    }

    private void ensureFileExists(String absolutePath, String relativePath) {
        final File file = new File(absolutePath, relativePath);

        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        if (!file.exists()) {
            try {
                Files.writeString(file.toPath(), "[]", StandardCharsets.UTF_8);
            } catch (IOException e) {
                LOG.error("Error while creating %s".formatted(file.getAbsolutePath()));
            }
        }
    }

    /**
     * When a repository JSON path setting changes, try to move the existing file to the new location
     * to preserve the user's data. This method is safe: it will not overwrite an existing target file.
     *
     * @param oldOverrides previous per-repo overrides (may be null)
     * @param newOverrides new per-repo overrides (may be null)
     */
    public void renameRepoDebtJsonIfPathChanged(Map<String, String> oldOverrides,
                                                Map<String, String> newOverrides) {
        try {
            String defaultRel = settings.getState().getDebtFilePath(project);
            LinkedHashSet<String> roots = new LinkedHashSet<>();
            if (oldOverrides != null) roots.addAll(oldOverrides.keySet());
            if (newOverrides != null) roots.addAll(newOverrides.keySet());
            // Also include known roots from detection and from existing debt items
            getRepositories().stream()
                    .map(Repository::getRepositoryAbsolutePath)
                    .forEach(roots::add);

            for (String root : roots) {
                if (root == null || root.isBlank()) continue;
                String oldOverride = oldOverrides == null ? null : oldOverrides.get(root);
                String newOverride = newOverrides == null ? null : newOverrides.get(root);
                File oldFile = resolveRepoDebtFileWithOverride(root, oldOverride, defaultRel);
                File newFile = resolveRepoDebtFileWithOverride(root, newOverride, defaultRel);
                // Normalize
                Path oldPath = oldFile.toPath().toAbsolutePath().normalize();
                Path newPath = newFile.toPath().toAbsolutePath().normalize();
                if (oldPath.equals(newPath)) {
                    continue; // nothing to do
                }
                if (!Files.exists(oldPath)) {
                    if (LOG.isDebugEnabled())
                        LOG.debug("renameRepoDebtJsonIfPathChanged: old file does not exist, skip. root=" + root + " old=" + oldPath);
                    continue;
                }
                if (Files.exists(newPath)) {
                    LOG.warn("Target debt JSON already exists, will not overwrite. root=" + root + " target=" + newPath);
                    continue;
                }
                try {
                    File parent = newPath.getParent() != null ? newPath.getParent().toFile() : null;
                    if (parent != null && !parent.exists()) parent.mkdirs();
                    Files.move(oldPath, newPath);
                    LOG.info("Moved repo debt JSON file: " + oldPath + " -> " + newPath);
                } catch (Exception moveEx) {
                    LOG.warn("Failed to move repo debt JSON file: " + oldPath + " -> " + newPath + ". Reason: " + moveEx.getMessage(), moveEx);
                }
            }
        } catch (Throwable t) {
            LOG.warn("renameRepoDebtJsonIfPathChanged failed: " + t.getMessage(), t);
        }
    }

    private void refreshHighlighting() {
        if (LOG.isDebugEnabled()) LOG.debug("refreshHighlighting invoked");
        DaemonCodeAnalyzer.getInstance(project).restart();
    }

    private List<Repository> getRepositories() {
        final RepositoriesService repositoriesService = project.getService(RepositoriesService.class);
        return repositoriesService.getRepositories();
    }

    public String findRepoRootForAbsolutePath(String absolutePath) {
        if (absolutePath == null || absolutePath.isBlank()) return "";
        String abs;
        try {
            abs = Paths.get(absolutePath).toAbsolutePath().normalize().toString();
        } catch (Exception e) {
            abs = absolutePath;
        }
        for (Repository repository : getRepositories()) {
            try {
                Path r = Paths.get(repository.getRepositoryAbsolutePath()).toAbsolutePath().normalize();
                Path a = Paths.get(abs).toAbsolutePath().normalize();
                if (a.startsWith(r)) return r.toString();
            } catch (Exception ignore) {
            }
        }
        return "";
    }

    public String toRepoRelative(String anyPath, String repoRoot) {
        if (anyPath == null) return "";
        try {
            Path p = Paths.get(anyPath);
            Path abs = p.isAbsolute() ? p.toAbsolutePath().normalize() : p.toAbsolutePath().normalize();
            if (repoRoot != null && !repoRoot.isBlank()) {
                Path root = Paths.get(repoRoot).toAbsolutePath().normalize();
                if (abs.startsWith(root)) {
                    return root.relativize(abs).toString().replace('\\', '/');
                }
            }
            return abs.toString().replace('\\', '/');
        } catch (Exception e) {
            return anyPath.replace('\\', '/');
        }
    }

    public Map<Repository, List<DebtItem>> getDebtsByRepository() {
        return debtsByRepository;
    }

    public void refresh() {
        final RepositoriesService repositoriesService = project.getService(RepositoriesService.class);
        repositoriesService.refreshFromMisc();

        project.getMessageBus().syncPublisher(TOPIC).refresh();
    }

    //TODO Should be private. split this sevice with WriterService
    @VisibleForTesting
    public static final class DebtItemDeserializer implements JsonDeserializer<DebtItem> {
        @Override
        public DebtItem deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            JsonObject obj = json.getAsJsonObject();

            // New fields (backward compatible)
            String id = getAsString(obj, "id", null);

            // Core fields
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
            int estimation = getAsInt(obj, "estimation", 0);
            String jira = getAsString(obj, "jira", "");

            // Optional, backward-compatible: prefer currentModule, fallback to legacy moduleParent
            String currentModule = getAsString(obj, "currentModule", null);
            if (currentModule == null || currentModule.isBlank()) {
                currentModule = getAsString(obj, "moduleParent", "");
            }

            // Parse links map if present (new format: Map<String, List<Relationship>>)
            Map<String, Relationship> links = new LinkedHashMap<>();
            JsonElement linksEl = obj.get("links");
            if (linksEl != null && linksEl.isJsonObject()) {
                for (Map.Entry<String, JsonElement> e : linksEl.getAsJsonObject().entrySet()) {
                    String key = e.getKey();
                    JsonElement val = e.getValue();
                    try {
                        Relationship relationship = null;
                        if (val != null && !val.isJsonNull()) {
                            if (val.isJsonArray()) {
                                for (JsonElement el : val.getAsJsonArray()) {
                                    try {
                                        if (el != null && !el.isJsonNull()) {
                                            relationship = Relationship.valueOf(el.getAsString());
                                        }
                                    } catch (Exception ignoreEach) {
                                    }
                                }
                            } else if (val.isJsonPrimitive()) {
                                // Backward compatibility with old single value
                                String relStr = val.getAsString();
                                if (relStr != null && !relStr.isBlank()) {
                                    relationship = Relationship.valueOf(relStr);
                                }
                            }
                        }
                        if (relationship != null) {
                            links.put(key, relationship);
                        }
                    } catch (Exception ignore) {
                        // skip malformed entries
                    }
                }
            }

            DebtItem.Builder builder = DebtItem.newBuilder()
                    .withFile(file)
                    .withLine(line)
                    .withTitle(title)
                    .withDescription(description)
                    .withUsername(username)
                    .withWantedLevel(wantedLevel)
                    .withComplexity(complexity)
                    .withStatus(status)
                    .withPriority(priority)
                    .withRisk(risk)
                    .withTargetVersion(targetVersion)
                    .withComment(comment)
                    .withEstimation(estimation)
                    .withCurrentModule(currentModule)
                    .withLinks(links)
                    .withJira(jira);
            if (id != null && !id.isBlank()) {
                builder.withId(id);
            }
            return builder.build();
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

    public static String resolveCurrentModule(String filePath, String projectBasePath) {
        if (filePath == null) {
            return null;
        }

        try {
            Path start = Paths.get(filePath).toAbsolutePath();
            Path root = projectBasePath != null ? Paths.get(projectBasePath).toAbsolutePath() : start.getRoot();
            Path dir = start.getParent();
            while (dir != null) {
                File pom = dir.resolve("pom.xml").toFile();
                if (pom.exists()) {
                    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                    dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
                    dbf.setNamespaceAware(false);
                    DocumentBuilder db = dbf.newDocumentBuilder();
                    Document doc = db.parse(pom);
                    Element project = doc.getDocumentElement();
                    if (project != null) {
                        // Read only direct children to avoid picking values from <parent>
                        String artifactId = textOfDirectChild(project, "artifactId");
                        String groupId = textOfDirectChild(project, "groupId");
                        if (groupId == null || groupId.isBlank()) {
                            var parents = project.getElementsByTagName("parent");
                            if (parents.getLength() > 0) {
                                Element parent = (Element) parents.item(0);
                                String parentGroupId = textOfDirectChild(parent, "groupId");
                                if (parentGroupId != null && !parentGroupId.isBlank()) {
                                    groupId = parentGroupId;
                                }
                            }
                        }
                        if (artifactId != null && !artifactId.isBlank()) {
                            if (groupId != null && !groupId.isBlank()) {
                                return groupId + ":" + artifactId;
                            } else {
                                return artifactId;
                            }
                        }
                    }
                    // found a pom, stop searching upwards regardless
                    break;
                }
                if (dir.equals(root)) break;
                dir = dir.getParent();
            }
        } catch (Exception exception) {
            LOG.warn("Error while searching for Maven Repository", exception);
        }
        return null;
    }

    private static String textOfDirectChild(Element parent, String tag) {
        for (org.w3c.dom.Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                Element e = (Element) n;
                if (tag.equals(e.getTagName())) {
                    return e.getTextContent();
                }
            }
        }
        return null;
    }
}
