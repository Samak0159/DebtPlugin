package com.github.fligneul.debtplugin.debt.service;

import com.github.fligneul.debtplugin.debt.listener.DebtDocumentListener;
import com.github.fligneul.debtplugin.debt.listener.DebtVfsListener;
import com.github.fligneul.debtplugin.debt.model.DebtItem;
import com.github.fligneul.debtplugin.debt.model.Repository;
import com.github.fligneul.debtplugin.debt.service.json.DebtReaderService;
import com.github.fligneul.debtplugin.debt.service.json.DebtWriterService;
import com.github.fligneul.debtplugin.debt.settings.DebtSettings;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
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
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service(Service.Level.PROJECT)
public final class DebtService {
    public static final Topic<DebtServiceListener> TOPIC = Topic.create("Debt Service Changed", DebtServiceListener.class);
    public static final Topic<DebtServiceSelectionListener> SELECTION_TOPIC = Topic.create("Select Changed", DebtServiceSelectionListener.class);

    private static final Logger LOG = Logger.getInstance(DebtService.class);

    private final Project project;
    private final DebtSettings settings;
    private final DebtWriterService debtWriterService;
    private final DebtReaderService debtReaderService;
    // Unified storage: key = repository, value = items in that repo
    private final Map<Repository, List<DebtItem>> debtsByRepository = new LinkedHashMap<>();

    public DebtService(@NotNull Project project) {
        this.project = Objects.requireNonNull(project, "project");
        this.settings = project.getService(DebtSettings.class);
        debtWriterService = new DebtWriterService();
        debtReaderService = new DebtReaderService();

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

        NotificationGroupManager.getInstance()
                .getNotificationGroup("Debt Notification Group")
                .createNotification("New item added", debtItem.getTitle(), NotificationType.INFORMATION)
                .notify(project);

        refresh();
        refreshHighlighting();
    }

    public Optional<Map.Entry<Repository, List<DebtItem>>> getDebtForRepositoryAbsolutePath(final @NotNull String repoRoot) {
        return debtsByRepository.entrySet()
                .stream()
                .filter(entry -> entry.getKey().getRepositoryAbsolutePath().equals(repoRoot))
                .findFirst();
    }

    public LinkedHashMap<String, Integer> extractModules(final List<DebtItem> debts) {
        final LinkedHashMap<String, Integer> modules = new LinkedHashMap<>();

        for (DebtItem debtItem : debts) {
            String module = debtItem.getCurrentModule();
            if (module == null || module.isBlank()) module = "Unknown";
            modules.put(module, modules.getOrDefault(module, 0) + 1);
        }

        return modules;
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
        if (oldDebtItem.equals(newDebtItem)) {
            LOG.debug("No update, old an newItem are the same");
        } else {
            final List<DebtItem> debts = entry.getValue();

            final int currentIndex = debts.indexOf(oldDebtItem);

            debts.set(currentIndex, newDebtItem);

            LOG.info("Updated debt: " + newDebtItem);

            saveDebts();

            NotificationGroupManager.getInstance()
                    .getNotificationGroup("Debt Notification Group")
                    .createNotification("Item updated", newDebtItem.getTitle(), NotificationType.INFORMATION)
                    .notify(project);

            refresh();
            refreshHighlighting();
        }
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
        String absolutPath = settings.getState().getDebtFilePath(project);
        for (Repository repository : repositories) {
            try {
                File jsonFile = resolveRepoDebtFile(repository.getRepositoryAbsolutePath(), absolutPath);
                if (!jsonFile.exists()) {
                    debtsByRepository.put(repository, new ArrayList<>());
                    continue;
                }
                List<DebtItem> loaded = this.debtReaderService.readDebts(jsonFile);

                debtsByRepository.put(repository, new ArrayList<>(loaded));
                LOG.info("Loaded debts total=%s from repos=%s".formatted(loaded.size(), repositories.size()));
            } catch (Exception ex) {
                LOG.warn("Failed loading debts for repoRoot=" + repository + ": " + ex.getMessage(), ex);
            }
        }

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
        final List<DebtItem> items = entry.getValue();

        final File jsonAbsolutePathFile = new File(repository.getRepositoryAbsolutePath(), repository.getJsonPath());

        if (this.debtWriterService.write(jsonAbsolutePathFile, items)) {
            LOG.info("Saved repo debts. repoRoot=%s count=%s path=%s".formatted(
                    repository.getRepositoryAbsolutePath(),
                    items.size(),
                    jsonAbsolutePathFile.toString()));
        }
    }

    private File resolveRepoDebtFile(String repoRoot, String absolutPath) {
        try {
            Map<String, String> overrides = settings.getState().getRepoDebtPaths();
            String val = overrides != null ? overrides.get(repoRoot) : null;
            return resolveRepoDebtFileWithOverride(repoRoot, val, absolutPath);
        } catch (Exception e) {
            return new File(repoRoot, absolutPath);
        }
    }

    private static File resolveRepoDebtFileWithOverride(String repoRoot, String overrideOrBlank, String defaultAbsolutPath) {
        try {
            if (overrideOrBlank == null || overrideOrBlank.isBlank()) {
                return new File(defaultAbsolutPath);
            }
            File file = new File(overrideOrBlank);
            return file.isAbsolute() ? file : new File(repoRoot, overrideOrBlank);
        } catch (Exception e) {
            return new File(repoRoot, defaultAbsolutPath);
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
            String defaultAbsolutPath = settings.getState().getDebtFilePath(project);
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
                File oldFile = resolveRepoDebtFileWithOverride(root, oldOverride, defaultAbsolutPath);
                File newFile = resolveRepoDebtFileWithOverride(root, newOverride, defaultAbsolutPath);
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

        repositoriesService.refreshAndLoadDebts();

        project.getMessageBus().syncPublisher(TOPIC).refresh();
    }

    public void getDistinctPriorities(final List<DebtItem> debtItems, final Consumer<String> priorityConsumer) {
        debtItems
                .stream()
                .map(DebtItem::getPriority)
                .map(String::trim)
                .collect(Collectors.toSet())
                .forEach(priorityConsumer);
    }

    public void getDistinctType(final List<DebtItem> debtItems, final Consumer<String> typeConsumer) {
        debtItems
                .stream()
                .map(DebtItem::getType)
                .map(String::trim)
                .collect(Collectors.toSet())
                .forEach(typeConsumer);
    }

    @Nullable
    public static String resolveCurrentModule(@Nullable String filePath, @Nullable String projectBasePath) {
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

    @Nullable
    private static String textOfDirectChild(Element parent, String tag) {
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element e = (Element) node;
                if (tag.equals(e.getTagName())) {
                    return e.getTextContent();
                }
            }
        }
        return null;
    }
}
