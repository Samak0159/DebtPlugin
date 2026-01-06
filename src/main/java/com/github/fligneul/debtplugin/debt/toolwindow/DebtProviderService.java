package com.github.fligneul.debtplugin.debt.toolwindow;

import com.github.fligneul.debtplugin.debt.model.DebtItem;
import com.github.fligneul.debtplugin.debt.model.Repository;
import com.github.fligneul.debtplugin.debt.service.DebtService;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service(Service.Level.PROJECT)
public final class DebtProviderService {
    private final DebtService debtService;
    private List<Repository> selectedRepositories = List.of();

    public DebtProviderService(final Project project) {
        this.debtService = project.getService(DebtService.class);
    }


    public List<DebtItem> currentItems() {
        if (selectedRepositories.isEmpty()) {
            return debtService.all();
        }

        return selectedRepositories
                .stream()
                .map(Repository::getRepositoryAbsolutePath)
                .map(debtService::getDebtForRepositoryAbsolutePath)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(Map.Entry::getValue)
                .flatMap(Collection::stream)
                .toList();
    }

    public void updateRepositories(final List<Repository> selectedRepositories) {
        this.selectedRepositories = selectedRepositories;
    }
}
