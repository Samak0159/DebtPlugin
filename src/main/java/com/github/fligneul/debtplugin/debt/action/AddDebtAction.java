package com.github.fligneul.debtplugin.debt.action;

import com.github.fligneul.debtplugin.debt.model.DebtItem;
import com.github.fligneul.debtplugin.debt.service.DebtService;
import com.github.fligneul.debtplugin.debt.settings.DebtSettings;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public class AddDebtAction extends AnAction {
    private static final Logger LOG = Logger.getInstance(AddDebtAction.class);

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;
        var editor = e.getData(CommonDataKeys.EDITOR);
        if (editor == null) return;
        VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
        if (file == null) return;

        // Prefill dialog with current file and line
        String initialPath = file.getPath();
        int initialLine = editor.getCaretModel().getLogicalPosition().line + 1;
        AddDebtDialog dialog = new AddDebtDialog(project, initialPath, initialLine);
        if (dialog.showAndGet()) {
            DebtService debtService = project.getService(DebtService.class);
            DebtSettings settings = project.getService(DebtSettings.class);
            String username = settings.getOrInitUsername();

            // Resolve file path chosen by user; fall back to initial if blank
            String absolute = dialog.getFilePath() != null && !dialog.getFilePath().isBlank() ? dialog.getFilePath() : initialPath;
            // Determine the repository root and store repo-relative path
            DebtService ds = project.getService(DebtService.class);
            String repoRoot = ds.findRepoRootForAbsolutePath(absolute);
            String storedPath = repoRoot.isEmpty() ? absolute : ds.toRepoRelative(absolute, repoRoot);

            final DebtItem debtItem = DebtItem.newBuilder()
                    .withId(dialog.getId())
                    .withFile(storedPath)
                    .withLine(Math.max(1, dialog.getLine()))
                    .withTitle(dialog.getTitleText())
                    .withDescription(dialog.getDescription())
                    .withUsername(username)
                    .withWantedLevel(dialog.getWantedLevel())
                    .withComplexity(dialog.getComplexity())
                    .withStatus(dialog.getStatus())
                    .withPriority(dialog.getPriority())
                    .withRisk(dialog.getRisk())
                    .withTargetVersion(dialog.getTargetVersion())
                    .withComment(dialog.getComment())
                    .withEstimation(dialog.getEstimation())
                    .withCurrentModule(DebtService.resolveCurrentModule(absolute, project.getBasePath()))
                    .build();

            LOG.info("Add debt confirmed: file=" + debtItem.getFile() + ":" + debtItem.getLine() +
                    " title=\"" + debtItem.getTitle() + "\"" +
                    " user=" + debtItem.getUsername());
            debtService.add(debtItem, repoRoot);
        } else {
            if (LOG.isDebugEnabled()) LOG.debug("Add debt dialog canceled");
        }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        var editor = e.getData(CommonDataKeys.EDITOR);
        e.getPresentation().setEnabledAndVisible(e.getProject() != null && editor != null);
    }

    @NotNull
    @Override
    public ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}