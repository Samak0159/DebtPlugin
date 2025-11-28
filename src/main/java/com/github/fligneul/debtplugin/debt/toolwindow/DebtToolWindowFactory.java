package com.github.fligneul.debtplugin.debt.toolwindow;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.openapi.wm.ex.ToolWindowEx;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

public class DebtToolWindowFactory implements ToolWindowFactory {
    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        DebtToolWindow debtToolWindow = new DebtToolWindow(project);
        Content content = ContentFactory.getInstance().createContent(debtToolWindow.getContent(), null, false);
        toolWindow.getContentManager().addContent(content);

        // Add a Refresh action to the toolwindow title bar (on the right)
        AnAction refreshAction = new AnAction("Refresh", "Refresh debts and modules", AllIcons.Actions.Refresh) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                debtToolWindow.refresh();
            }
        };
        if (toolWindow instanceof ToolWindowEx twEx) {
            twEx.setTitleActions(refreshAction);
        }
    }
}
