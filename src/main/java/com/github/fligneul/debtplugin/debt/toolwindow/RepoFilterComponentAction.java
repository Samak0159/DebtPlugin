package com.github.fligneul.debtplugin.debt.toolwindow;

import com.github.fligneul.debtplugin.debt.model.Repository;
import com.github.fligneul.debtplugin.debt.service.DebtService;
import com.github.fligneul.debtplugin.debt.service.RepositoriesService;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;
import java.util.List;
import java.util.Set;

public class RepoFilterComponentAction extends AnAction implements CustomComponentAction {
    private final Project project;
    private MultiSelectFilter<String> filter;

    public RepoFilterComponentAction(Project project) {
        super("Repositories");
        this.project = project;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        // No-op: interactions happen via the custom component
    }

    @Override
    public JComponent createCustomComponent(@NotNull Presentation presentation, @NotNull String place) {
        if (filter == null) {
            filter = new MultiSelectFilter<>("Repositories");
            RepositoriesService service = project.getService(RepositoriesService.class);

            List<String> names = service.getRepositories()
                    .stream()
                    .map(Repository::getRepositoryName)
                    .toList();

            filter.setOptions(names);

            final DebtProviderService providerService = project.getService(DebtProviderService.class);
            filter.addSelectionListener(() -> {
                final Set<String> repositories = filter.getSelected();

                final List<Repository> selectedRepositories = service.getRepositories()
                        .stream()
                        .filter(repository -> repositories.contains(repository.getRepositoryName()))
                        .toList();

                providerService.updateRepositories(selectedRepositories);

                final DebtService debtService = project.getService(DebtService.class);
                debtService.refresh();
            });
        }
        return filter;
    }

}
