package com.github.fligneul.debtplugin.debt.toolwindow;

import com.github.fligneul.debtplugin.debt.toolwindow.action.AddDebtAction;
import com.github.fligneul.debtplugin.debt.toolwindow.action.RepoFilterComponentAction;
import com.intellij.openapi.actionSystem.AnAction;
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
        final DebtToolWindow debtToolWindow = new DebtToolWindow(project);

        final Content content = ContentFactory.getInstance().createContent(debtToolWindow, null, false);
        toolWindow.getContentManager().addContent(content);
        LOG.info("DebtToolWindow content created");

        // Add an "Add Debt" action to the toolwindow title bar
        final AnAction addDebtAction = new AddDebtAction(project);

        // Add a Refresh action to the toolwindow title bar (on the right)
        final AnAction refreshAction = new RefreshDebtAction(project);

        // Add a Repositories multi-select filter next to Refresh
        final AnAction reposFilterAction = new RepoFilterComponentAction(project);

        if (toolWindow instanceof ToolWindowEx twEx) {
            if (LOG.isDebugEnabled()) LOG.debug("Setting title bar actions (Add Debt + Refresh + Repositories filter)");
            twEx.setTitleActions(List.of(addDebtAction, refreshAction, reposFilterAction));
        }
    }
}
