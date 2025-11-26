package com.github.fligneul.debtplugin.debt.toolwindow;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

public class DebtToolWindowFactory implements ToolWindowFactory {
    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        DebtToolWindow debtToolWindow = new DebtToolWindow(project);
        Content content = ContentFactory.getInstance().createContent(debtToolWindow.getContent(), null, false);
        toolWindow.getContentManager().addContent(content);
    }
}
