package com.github.fligneul.debtplugin.debt.listener;

import com.github.fligneul.debtplugin.debt.model.DebtItem;
import com.github.fligneul.debtplugin.debt.service.DebtService;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent;
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Listens to Virtual File System changes to keep stored debt file paths in sync
 * when files or directories are renamed or moved.
 */
public final class DebtVfsListener implements BulkFileListener {
    private static final Logger LOG = Logger.getInstance(DebtVfsListener.class);

    private final Project project;

    public DebtVfsListener(@NotNull Project project) {
        this.project = project;
    }

    @Override
    public void after(@NotNull List<? extends @NotNull VFileEvent> events) {
        try {
            if (events.isEmpty()) return;
            DebtService debtService = project.getService(DebtService.class);
            String basePath = project.getBasePath();

            // Collect runnable updates and apply at the end to avoid concurrent modification
            List<Runnable> updates = new ArrayList<>();

            for (VFileEvent e : events) {
                try {
                    if (e instanceof VFileMoveEvent move) {
                        VirtualFile file = move.getFile();
                        if (file == null) continue;
                        boolean isDir = file.isDirectory();
                        String oldPathAbs = buildPath(move.getOldParent().getPath(), file.getName());
                        String newPathAbs = buildPath(move.getNewParent().getPath(), file.getName());
                        String oldRel = toProjectRelativeSafe(oldPathAbs, basePath);
                        String newRel = toProjectRelativeSafe(newPathAbs, basePath);
                        collectUpdatesForPathChange(debtService, oldRel, newRel, isDir, updates);
                    } else if (e instanceof VFilePropertyChangeEvent prop) {
                        if (!Objects.equals(prop.getPropertyName(), VirtualFile.PROP_NAME)) continue; // rename only
                        VirtualFile file = prop.getFile();
                        if (file == null) continue;
                        boolean isDir = file.isDirectory();
                        String parent = file.getParent() != null ? file.getParent().getPath() : null;
                        String oldPathAbs = buildPath(parent, Objects.toString(prop.getOldValue(), file.getName()));
                        String newPathAbs = buildPath(parent, Objects.toString(prop.getNewValue(), file.getName()));
                        String oldRel = toProjectRelativeSafe(oldPathAbs, basePath);
                        String newRel = toProjectRelativeSafe(newPathAbs, basePath);
                        collectUpdatesForPathChange(debtService, oldRel, newRel, isDir, updates);
                    }
                } catch (Exception perEventEx) {
                    LOG.warn("DebtVfsListener: failed to process event: " + perEventEx.getMessage(), perEventEx);
                }
            }

            if (!updates.isEmpty()) {
                LOG.info("DebtVfsListener: applying " + updates.size() + " file path update(s)");
                for (Runnable r : updates) r.run();
            }
        } catch (Exception ex) {
            LOG.warn("DebtVfsListener.after failed: " + ex.getMessage(), ex);
        }
    }

    private static String buildPath(String parent, String name) {
        if (parent == null || parent.isBlank()) return name;
        String p = parent.endsWith("/") || parent.endsWith("\\") ? parent : parent + "/";
        return (p + name).replace('\\', '/');
    }

    private static void collectUpdatesForPathChange(DebtService service,
                                                    String oldRel,
                                                    String newRel,
                                                    boolean isDirectory,
                                                    List<Runnable> outUpdates) {
        List<DebtItem> all = service.all();
        String oldRelNorm = normalizeForCompare(oldRel);
        String oldDirPrefix = isDirectory ? ensureTrailingSlash(oldRel) : null;
        for (DebtItem d : all) {
            String stored = d.getFile();
            String storedNorm = normalizeForCompare(stored);
            if (!isDirectory) {
                // Exact file move/rename
                if (storedNorm.equalsIgnoreCase(oldRelNorm)) {
                    final DebtItem oldItem = d;
                    final String newFile = newRel;
                    outUpdates.add(() -> applyFileUpdate(service, oldItem, newFile));
                }
            } else {
                // Directory move/rename: replace prefix
                String storedWithSlash = ensureTrailingSlash(stored);
                if (storedWithSlash.toLowerCase(Locale.getDefault()).startsWith(ensureTrailingSlash(oldRel).toLowerCase(Locale.getDefault()))) {
                    String suffix = stored.substring(oldRel.length());
                    String updatedPath = ensureTrailingSlash(newRel) + trimLeadingSlash(suffix);
                    final DebtItem oldItem = d;
                    final String newFile = updatedPath;
                    outUpdates.add(() -> applyFileUpdate(service, oldItem, newFile));
                }
            }
        }
    }

    private static void applyFileUpdate(DebtService service, DebtItem oldItem, String newFile) {
        DebtItem updated = new DebtItem(
                newFile,
                oldItem.getLine(),
                oldItem.getTitle(),
                oldItem.getDescription(),
                oldItem.getUsername(),
                oldItem.getWantedLevel(),
                oldItem.getComplexity(),
                oldItem.getStatus(),
                oldItem.getPriority(),
                oldItem.getRisk(),
                oldItem.getTargetVersion(),
                oldItem.getComment()
        );
        updated.setCurrentModule(oldItem.getCurrentModule());
        service.update(oldItem, updated);
    }

    private static String ensureTrailingSlash(String p) {
        if (p == null || p.isEmpty()) return p;
        String s = p.replace('\\', '/');
        return s.endsWith("/") ? s : s + "/";
        
    }

    private static String trimLeadingSlash(String p) {
        if (p == null) return "";
        String s = p.replace('\\', '/');
        while (s.startsWith("/")) s = s.substring(1);
        return s;
    }

    private static String normalizeForCompare(String any) {
        if (any == null) return "";
        return any.replace('\\', '/');
    }

    private static String toProjectRelativeSafe(String anyPath, String basePath) {
        if (anyPath == null) return "";
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
            return anyPath.replace('\\', '/');
        }
    }
}
