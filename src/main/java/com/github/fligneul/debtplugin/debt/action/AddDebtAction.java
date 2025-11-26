package com.github.fligneul.debtplugin.debt.action;

import com.github.fligneul.debtplugin.debt.model.DebtItem;
import com.github.fligneul.debtplugin.debt.service.DebtService;
import com.github.fligneul.debtplugin.debt.settings.DebtSettings;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public class AddDebtAction extends AnAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;
        var editor = e.getData(CommonDataKeys.EDITOR);
        if (editor == null) return;
        VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
        if (file == null) return;

        AddDebtDialog dialog = new AddDebtDialog();
        if (dialog.showAndGet()) {
            DebtService debtService = project.getService(DebtService.class);
            DebtSettings settings = project.getService(DebtSettings.class);
            String username = settings.getOrInitUsername();
            DebtItem debtItem = new DebtItem(
                    file.getPath(),
                    editor.getCaretModel().getLogicalPosition().line + 1,
                    dialog.getTitleText(),
                    dialog.getDescription(),
                    username,
                    dialog.getWantedLevel(),
                    dialog.getComplexity(),
                    dialog.getStatus(),
                    dialog.getPriority(),
                    dialog.getRisk(),
                    dialog.getTargetVersion(),
                    ""
            );
            debtService.add(debtItem);
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
