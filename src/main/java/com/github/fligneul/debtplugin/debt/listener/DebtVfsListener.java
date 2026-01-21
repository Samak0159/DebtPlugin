package com.github.fligneul.debtplugin.debt.listener;

import com.github.fligneul.debtplugin.debt.model.DebtItem;
import com.github.fligneul.debtplugin.debt.model.Repository;
import com.github.fligneul.debtplugin.debt.service.DebtService;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent;
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
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

            for (VFileEvent event : events) {
                try {
                    final VirtualFile file;
                    final String oldPathAbs;
                    final String newPathAbs;
                    if (event instanceof VFileMoveEvent move) {
                        file = move.getFile();
                        oldPathAbs = buildPath(move.getOldParent().getPath(), file.getName());
                        newPathAbs = buildPath(move.getNewParent().getPath(), file.getName());
                    } else if (event instanceof VFilePropertyChangeEvent prop) {
                        if (!Objects.equals(prop.getPropertyName(), VirtualFile.PROP_NAME)) continue; // rename only
                        file = prop.getFile();
                        String parent = file.getParent() != null ? file.getParent().getPath() : null;
                        oldPathAbs = buildPath(parent, Objects.toString(prop.getOldValue(), file.getName()));
                        newPathAbs = buildPath(parent, Objects.toString(prop.getNewValue(), file.getName()));
                    } else {
                        throw new RuntimeException("Event not handle");
                    }

                    String oldRepoRoot = debtService.findRepoRootForAbsolutePath(oldPathAbs);
                    String newRepoRoot = debtService.findRepoRootForAbsolutePath(newPathAbs);
                    String oldRel = oldRepoRoot.isEmpty() ? toProjectRelativeSafe(oldPathAbs, basePath) : debtService.toRepoRelative(oldPathAbs, oldRepoRoot);
                    String newRel = oldRepoRoot.isEmpty() ? toProjectRelativeSafe(newPathAbs, basePath) : debtService.toRepoRelative(newPathAbs, oldRepoRoot);
                    updateItems(debtService,
                            oldRepoRoot,
                            newRepoRoot,
                            oldRel,
                            newRel);

                } catch (Exception perEventEx) {
                    LOG.warn("DebtVfsListener: failed to process event: " + perEventEx.getMessage(), perEventEx);
                }
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

    private void updateItems(DebtService service,
                             String oldRepoRoot,
                             String newRepoRoot,
                             String oldRel,
                             String newRel) {

        if (oldRepoRoot.equals(newRepoRoot)) {
            // File has been moved or renamed in the same Repository
            final Map.Entry<Repository, List<DebtItem>> debtsForRepository = service.getDebtForRepositoryAbsolutePath(oldRepoRoot)
                    .orElseThrow();

            debtsForRepository.getValue()
                    .stream()
                    .filter(debtItem -> debtItem.getFile().equals(oldRel))
                    .findFirst()
                    .ifPresent(oldItem -> applyFileUpdate(debtsForRepository, service, oldItem, newRel));
        } else {
            // File has been moved or renamed in another Repository
            final Map.Entry<Repository, List<DebtItem>> oldRepository = service.getDebtForRepositoryAbsolutePath(oldRepoRoot)
                    .orElseThrow();

            DebtItem oldItem = null;
            int index = -1;
            for (int i = 0; i < oldRepository.getValue().size(); i++) {
                final DebtItem debtItem = oldRepository.getValue().get(i);
                if (debtItem.getFile().equals(oldRel)) {
                    oldItem = debtItem;
                    index = i;
                    break;
                }
            }

            if (index > -1) {
                oldRepository.getValue().remove(index);
            }

            if (oldItem != null) {
                final DebtItem updated = oldItem
                        .toBuilder()
                        .withFile(newRel)
                        .build();

                service.add(updated, newRepoRoot);
            }
        }
    }

    private void applyFileUpdate(final Map.Entry<Repository, List<DebtItem>> debtsForRepository, DebtService service, DebtItem oldItem, String newFile) {
        final DebtItem updated = oldItem.toBuilder()
                .withFile(newFile)
                .build();

        service.update(debtsForRepository, oldItem, updated);
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
