package com.github.fligneul.debtplugin.debt.action.links;

import com.github.fligneul.debtplugin.debt.toolwindow.DebtProviderService;

import javax.swing.DefaultComboBoxModel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

public class LinkComboModel extends DefaultComboBoxModel<LinkDebtItem> {
    private static final LinkDebtItem DEFAULT_IF_EMPTY = new LinkDebtItem("", "");

    private final List<LinkDebtItem> items;
    private final LinkTableModel linksTableModel;

    private Consumer<Boolean> update;
    private final List<LinkDebtItem> originalDebt;

    public LinkComboModel(DebtProviderService debtProviderService, final LinkTableModel linksTableModel, final String idCurrentEditingDebt) {
        this.linksTableModel = linksTableModel;
        this.items = new ArrayList<>();

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
    public void addAll(Collection<? extends LinkDebtItem> collection) {
        collection.forEach(this::addElement);
    }

    public void addElement(final LinkDebtItem debtItem) {
        final boolean isEmpty = items.size() == 1 && DEFAULT_IF_EMPTY.equals(items.get(0));

        if (isEmpty) {
            super.removeElement(DEFAULT_IF_EMPTY);
            items.remove(DEFAULT_IF_EMPTY);
            update.accept(true);
        }

        super.addElement(debtItem);
        items.add(debtItem);
    }

    public void removeAllElements() {
        new ArrayList<>(items).forEach(this::removeElement);
    }

    public void removeElement(final LinkDebtItem debtItem) {
        super.removeElement(debtItem);
        items.remove(debtItem);

        if (items.isEmpty()) {
            items.add(DEFAULT_IF_EMPTY);
            addElement(DEFAULT_IF_EMPTY);
            update.accept(false);
        }
    }

    public void canAddListener(final Consumer<Boolean> upadte) {
        this.update = upadte;
    }

    public void filter(final String text) {
        removeAllElements();
        for (LinkDebtItem debtItem : originalDebt) {
            if (!linksTableModel.getLinks().containsKey(debtItem.id())
                    && !items.contains(debtItem)
                    && (text.isEmpty()
                    || debtItem.debtName().toLowerCase().contains(text))
            ) {
                addElement(debtItem);
            }
        }
    }
}
