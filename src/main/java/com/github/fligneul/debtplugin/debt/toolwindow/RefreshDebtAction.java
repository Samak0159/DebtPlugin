package com.github.fligneul.debtplugin.debt.toolwindow;

import com.github.fligneul.debtplugin.debt.service.DebtService;
import com.github.fligneul.debtplugin.debt.toolwindow.table.DebtTableContainer;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class RefreshDebtAction extends AnAction {
    private static final Logger LOG = Logger.getInstance(DebtTableContainer.class);

    private final Project project;

    public RefreshDebtAction(final Project project) {
        super("Refresh", "Refresh debts and modules", AllIcons.Actions.Refresh);
        this.project = project;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        if (LOG.isDebugEnabled()) LOG.debug("Refresh action invoked from title bar");
        final DebtService debtService = project.getService(DebtService.class);
        debtService.refresh();
    }

    ;
}
