package com.github.fligneul.debtplugin.debt.toolwindow;

import com.github.fligneul.debtplugin.debt.action.AddDebtDialog;
import com.github.fligneul.debtplugin.debt.model.DebtItem;
import com.github.fligneul.debtplugin.debt.service.DebtService;
import com.github.fligneul.debtplugin.debt.settings.DebtSettings;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.openapi.wm.ex.ToolWindowEx;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class DebtToolWindowFactory implements ToolWindowFactory {
    private static final Logger LOG = Logger.getInstance(DebtToolWindowFactory.class);

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        DebtToolWindow debtToolWindow = new DebtToolWindow(project);
        Content content = ContentFactory.getInstance().createContent(debtToolWindow.getContent(), null, false);
        toolWindow.getContentManager().addContent(content);
        LOG.info("DebtToolWindow content created");

        // Add an "Add Debt" action to the toolwindow title bar
        AnAction addDebtAction = new AnAction("Add Debt", "Create a new debt", AllIcons.General.Add) {
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
        };

        // Add a Refresh action to the toolwindow title bar (on the right)
        AnAction refreshAction = new AnAction("Refresh", "Refresh debts and modules", AllIcons.Actions.Refresh) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                if (LOG.isDebugEnabled()) LOG.debug("Refresh action invoked from title bar");
                final DebtService debtService = project.getService(DebtService.class);
                debtService.refresh();
            }
        };

        // Add a Repositories multi-select filter next to Refresh
        AnAction reposFilterAction = new RepoFilterComponentAction(project);

        if (toolWindow instanceof ToolWindowEx twEx) {
            if (LOG.isDebugEnabled()) LOG.debug("Setting title bar actions (Add Debt + Refresh + Repositories filter)");
            twEx.setTitleActions(List.of(addDebtAction, refreshAction, reposFilterAction));
        }
    }
}
