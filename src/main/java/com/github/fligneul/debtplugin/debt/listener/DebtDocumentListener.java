package com.github.fligneul.debtplugin.debt.listener;

import com.github.fligneul.debtplugin.debt.model.DebtItem;
import com.github.fligneul.debtplugin.debt.service.DebtService;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Updates stored debt line numbers when the underlying file is edited.
 *
 * Basic rules:
 * - If lines are inserted/removed above a debt line, shift it by the line delta.
 * - If a change spans over a debt line, move the debt to the first changed line.
 */
public final class DebtDocumentListener implements DocumentListener {
    private static final Logger LOG = Logger.getInstance(DebtDocumentListener.class);

    private final Project project;

    public DebtDocumentListener(@NotNull Project project) {
        this.project = project;
    }

    @Override
    public void documentChanged(@NotNull DocumentEvent event) {
        try {
            Document doc = event.getDocument();
            VirtualFile vf = FileDocumentManager.getInstance().getFile(doc);
            if (vf == null) return;

            String filePath = toProjectRelativeSafe(vf.getPath(), project.getBasePath());

            // Compute line delta
            int startOffset = event.getOffset();
            int startLine = doc.getLineNumber(startOffset) + 1; // 1-based

            CharSequence oldFrag = event.getOldFragment();
            CharSequence newFrag = event.getNewFragment();
            int oldLines = countNewLines(oldFrag);
            int newLines = countNewLines(newFrag);
            // If change ends with a newline, treat as adding a line boundary
            // DocumentEvent fragments do not include implicit newline; the above is sufficient for shifts
            int delta = newLines - oldLines;

            if (delta == 0 && oldLines == 0 && newLines == 0) {
                // No line impact; nothing to do
                return;
            }

            int affectedLastLine = startLine + Math.max(oldLines, 0); // inclusive region end (pre-change)

            DebtService debtService = project.getService(DebtService.class);
            List<DebtItem> all = debtService.all();
            List<Runnable> updates = new ArrayList<>();

            for (DebtItem d : all) {
                String debtPath = toProjectRelativeSafe(d.getFile(), project.getBasePath());
                if (!equalsPathIgnoreCase(debtPath, filePath)) continue;

                int line = d.getLine();
                if (line > affectedLastLine) {
                    int newLine = line + delta;
                    if (newLine < 1) newLine = 1;
                    if (newLine != line) {
                        final DebtItem od = d;
                        final int nl = newLine;
                        updates.add(() -> applyLineUpdate(debtService, od, nl));
                    }
                } else if (line >= startLine && line <= affectedLastLine) {
                    // Change overlaps the debt line: attach it to the start of the changed region
                    int newLine = startLine;
                    if (newLine < 1) newLine = 1;
                    if (newLine != line) {
                        final DebtItem od2 = d;
                        final int nl2 = newLine;
                        updates.add(() -> applyLineUpdate(debtService, od2, nl2));
                    }
                }
            }

            // Apply collected updates
            if (!updates.isEmpty()) {
                LOG.info("DebtDocumentListener: applying " + updates.size() + " line update(s) for " + filePath +
                        " startLine=" + startLine + " delta=" + delta + " oldLines=" + oldLines + " newLines=" + newLines);
                // Run updates one by one; DebtService.update() persists and refreshes highlighting.
                // For performance we could batch, but keep it simple for now.
                for (Runnable r : updates) r.run();
            }
        } catch (Exception ex) {
            LOG.warn("DebtDocumentListener failed to process document change: " + ex.getMessage(), ex);
        }
    }

    private static void applyLineUpdate(DebtService service, DebtItem oldItem, int newLine) {
        final DebtItem updated = oldItem.toBuilder()
                .withLine(newLine)
                .build();

        service.update(oldItem, updated);
    }

    private static int countNewLines(CharSequence seq) {
        if (seq == null || seq.length() == 0) return 0;
        int c = 0;
        for (int i = 0; i < seq.length(); i++) {
            if (seq.charAt(i) == '\n') c++;
        }
        return c;
    }

    private static boolean equalsPathIgnoreCase(String a, String b) {
        if (a == null || b == null) return false;
        return a.replace('\\', '/').equalsIgnoreCase(b.replace('\\', '/'));
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
