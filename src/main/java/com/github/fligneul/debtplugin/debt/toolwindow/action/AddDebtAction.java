package com.github.fligneul.debtplugin.debt.toolwindow.action;

import com.github.fligneul.debtplugin.debt.action.AddDebtDialog;
import com.github.fligneul.debtplugin.debt.model.DebtItem;
import com.github.fligneul.debtplugin.debt.service.DebtService;
import com.github.fligneul.debtplugin.debt.settings.DebtSettings;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class AddDebtAction extends AnAction {
    private static final Logger LOG = Logger.getInstance(AddDebtAction.class);

    private final Project project;

    public AddDebtAction(final Project project) {
        super("Add Debt", "Create a new debt", AllIcons.General.Add);
        this.project = project;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        if (LOG.isDebugEnabled()) LOG.debug("Add Debt action invoked from title bar");
        AddDebtDialog dialog = new AddDebtDialog(project, "", 1);
        if (dialog.showAndGet()) {
            DebtService debtService = project.getService(DebtService.class);
            DebtSettings settings = project.getService(DebtSettings.class);
            String username = settings.getOrInitUsername();

            // Resolve file path chosen by user
            String absolute = dialog.getFilePath() != null && !dialog.getFilePath().isBlank() ? dialog.getFilePath() : "";
            final String repoRoot;
            final String storedPath;
            if (absolute.isBlank()) {
                repoRoot = dialog.getSelectedRepository().getRepositoryAbsolutePath();
                storedPath = "";
            } else {
                // Determine the repository root and store repo-relative path
                repoRoot = debtService.findRepoRootForAbsolutePath(absolute);
                storedPath = repoRoot.isEmpty() ? absolute : debtService.toRepoRelative(absolute, repoRoot);
            }

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
                    .withLinks(dialog.getLinks())
                    .withJira(dialog.getJira())
                    .build();

            LOG.info("Add debt confirmed: file=" + debtItem.getFile() + ":" + debtItem.getLine() +
                    " title=\"" + debtItem.getTitle() + "\"" +
                    " user=" + debtItem.getUsername());
            debtService.add(debtItem, repoRoot);
        } else {
            if (LOG.isDebugEnabled()) LOG.debug("Add debt dialog canceled");
        }
    }
}
