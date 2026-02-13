package com.github.fligneul.debtplugin.debt.action.links;

import com.github.fligneul.debtplugin.debt.service.DebtProviderService;
import com.intellij.ui.MutableCollectionComboBoxModel;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

public class LinkComboModel extends MutableCollectionComboBoxModel<LinkDebtItem> {
    private static final LinkDebtItem DEFAULT_IF_EMPTY = new LinkDebtItem("", "");

    private final LinkTableModel linksTableModel;

    private Consumer<Boolean> update;
    private final List<LinkDebtItem> originalDebt;

    public LinkComboModel(DebtProviderService debtProviderService, final LinkTableModel linksTableModel, final String idCurrentEditingDebt) {
        this.linksTableModel = linksTableModel;

        this.originalDebt = debtProviderService.currentItems()
                .stream()
                .filter(debt -> !idCurrentEditingDebt.equals(debt.getId()))
                .map(debtItem -> new LinkDebtItem(debtItem.getId(), debtItem.getTitle()))
                .toList();

        addAll(originalDebt
                .stream()
                .filter(debt -> !linksTableModel.getLinks().containsKey(debt.id()))
                .toList());
    }

    @Override
    public void update(@NotNull final List<? extends LinkDebtItem> items) {
        if (items.isEmpty()) {
            super.update(List.of(DEFAULT_IF_EMPTY));
            update.accept(false);
        } else {
            super.update(items);
            update.accept(true);
        }
    }

    public void addAll(Collection<? extends LinkDebtItem> collection) {
        collection.forEach(this::addElement);
    }

    public void addElement(final LinkDebtItem debtItem) {
        final boolean isEmpty = this.getInternalList().size() == 1 && DEFAULT_IF_EMPTY.equals(this.getInternalList().get(0));

        if (isEmpty) {
            this.getInternalList().remove(DEFAULT_IF_EMPTY);
            if (update != null) update.accept(true);
        }

        super.addElement(debtItem);
    }

    public void removeElement(final LinkDebtItem debtItem) {
        this.getInternalList().remove(debtItem);

        if (this.getInternalList().isEmpty()) {
            this.getInternalList().add(DEFAULT_IF_EMPTY);
            if (update != null) update.accept(false);
        }
    }

    public void canAddListener(final Consumer<Boolean> upadte) {
        this.update = upadte;
    }

    public void filter(final String text) {
        final List<LinkDebtItem> newList = new ArrayList<>();
        for (LinkDebtItem debtItem : originalDebt) {
            if (!linksTableModel.getLinks().containsKey(debtItem.id())
                    && (text.isEmpty()
                    || debtItem.debtName().toLowerCase().contains(text.toLowerCase()))
            ) {
                newList.add(debtItem);
            }
        }

        update(newList);
    }
}
