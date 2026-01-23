package com.github.fligneul.debtplugin.debt.action.links;

import com.github.fligneul.debtplugin.debt.model.Relationship;

import javax.swing.table.DefaultTableModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class LinkTableModel extends DefaultTableModel {
    private final List<LinkTitleRelation> links = new ArrayList<>();

    public LinkTableModel() {
        addColumn("Debt");
        addColumn("Relationship");
        addColumn("");// Action
    }

    @Override
    public boolean isCellEditable(int row, int column) {
        // Only the action column is editable to host the button cell editor
        return column == 2;
    }

    public void add(final LinkTitleRelation linkTitleRelation) {
        links.add(linkTitleRelation);
        addRow(linkTitleRelation.toObject());
    }

    public LinkTitleRelation remove(final int index) {
        final LinkTitleRelation removed = links.remove(index);
        removeRow(index);

        return removed;
    }

    public Map<String, Relationship> getLinks() {
        return links
                .stream()
                .collect(Collectors.toMap(LinkTitleRelation::debtId, LinkTitleRelation::relationship));
    }
}
