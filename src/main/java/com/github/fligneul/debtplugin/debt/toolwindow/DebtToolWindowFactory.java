package com.github.fligneul.debtplugin.debt.toolwindow;

import com.github.fligneul.debtplugin.debt.service.DebtService;
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

public class DebtToolWindowFactory implements ToolWindowFactory {
    private static final Logger LOG = Logger.getInstance(DebtToolWindowFactory.class);

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        DebtToolWindow debtToolWindow = new DebtToolWindow(project);
        Content content = ContentFactory.getInstance().createContent(debtToolWindow.getContent(), null, false);
        toolWindow.getContentManager().addContent(content);
        LOG.info("DebtToolWindow content created");

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
            if (LOG.isDebugEnabled()) LOG.debug("Setting title bar actions (Refresh + Repositories filter)");
            twEx.setTitleActions(refreshAction, reposFilterAction);
        }
    }
}
