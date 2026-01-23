package com.github.fligneul.debtplugin.debt.action.links;

import com.github.fligneul.debtplugin.debt.model.DebtItem;
import com.github.fligneul.debtplugin.debt.model.Relationship;
import com.github.fligneul.debtplugin.debt.service.DebtService;
import com.github.fligneul.debtplugin.debt.toolkit.SwingComponentHelper;
import com.github.fligneul.debtplugin.debt.toolwindow.DebtProviderService;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.table.TableColumn;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.Map;

public class LinksComponent {
    private static final Logger LOG = Logger.getInstance(LinksComponent.class);

    private final JPanel root;
    private final LinkComboModel debtComboModel;
    private final LinkTableModel linksTableModel;

    private final JBTable linksTable;
    private JComboBox<LinkDebtItem> linksDebtCombo;
    private JComboBox<Relationship> linksRelationList;

    public LinksComponent(final DebtService debtService,
                          final DebtProviderService debtProviderService,
                          final Map<String, Relationship> currentLinks,
                          final String idCurrentEditingDebt) {
        linksTableModel = new LinkTableModel();
        linksTable = new JBTable(linksTableModel);

        for (final Map.Entry<String, Relationship> debtIdRelationShip : currentLinks.entrySet()) {
            final String debtId = debtIdRelationShip.getKey();

            final String debtName = debtService
                    .all()
                    .stream()
                    .filter(debtItem -> debtItem.getId().equals(debtId))
                    .findFirst()
                    .map(DebtItem::getTitle)
                    .orElse("");

            linksTableModel.add(new LinkTitleRelation(debtId, debtName, debtIdRelationShip.getValue()));
        }

        debtComboModel = new LinkComboModel(debtProviderService, linksTableModel, idCurrentEditingDebt);


        final JPanel debtSelectionPanel = createDebtSectionPanel();

        final JPanel relationAndAddPanel = createRelationAndAddPanel();

        final JComponent tableComponent = createTablePanel();

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.add(debtSelectionPanel);
        content.add(Box.createVerticalStrut(8));
        content.add(relationAndAddPanel);
        content.add(Box.createVerticalStrut(8));
        content.add(tableComponent);

        // Wrap the whole content in a scroll pane so the labeled() logic treats it as scrollable
        JBScrollPane outerScroll = new JBScrollPane(content);
        outerScroll.setPreferredSize(new Dimension(420, 220));
        root = SwingComponentHelper.labeled("Links:", outerScroll);
    }

    private JPanel createDebtSectionPanel() {
        linksDebtCombo = new JComboBox<>(debtComboModel);
        linksDebtCombo.setEditable(false);
        // Simple renderer: show title (and id)
        linksDebtCombo.setRenderer((list, value, index, isSelected, cellHasFocus) -> new JLabel(value.debtName()));

        // Basic filter behavior: when user types in editor, filter items by title
        var debtFilterTextField = new JTextField(12);

        debtFilterTextField.addFocusListener(new FocusListener() {
            @Override
            public void focusGained(final FocusEvent e) {
                // Nothing to do
            }

            @Override
            public void focusLost(final FocusEvent e) {
                String text = debtFilterTextField.getText().trim().toLowerCase();
                debtComboModel.filter(text);
                linksDebtCombo.revalidate();
                linksDebtCombo.repaint();
            }
        });

        linksRelationList = new JComboBox<>(Relationship.values());
        linksRelationList.setEditable(false);

        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        panel.add(new JLabel("Debt filter:"));
        panel.add(debtFilterTextField);
        panel.add(Box.createVerticalStrut(4));
        panel.add(linksDebtCombo);
        return panel;
    }

    private JPanel createRelationAndAddPanel() {
        final JButton addLink = new JButton("+");
        addLink.addActionListener(event -> {
            final LinkDebtItem linkDebtItem = (LinkDebtItem) linksDebtCombo.getSelectedItem();
            final Relationship relationship = (Relationship) linksRelationList.getSelectedItem();
            if (linkDebtItem == null || relationship == null) return;
            linksTableModel.add(new LinkTitleRelation(linkDebtItem.id(), linkDebtItem.debtName(), relationship));

            debtComboModel.removeElement(linkDebtItem);
        });
        debtComboModel.canAddListener(addLink::setEnabled);

        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        panel.add(new JLabel("Relationship:"));
        panel.add(linksRelationList);
        panel.add(addLink);

        return panel;
    }

    private JComponent createTablePanel() {
        final ActionDeleteButtonsCell actionButtons = new ActionDeleteButtonsCell(rowNumber -> {
            int modelRow = linksTable.convertRowIndexToModel(rowNumber);
            if (modelRow >= 0 && modelRow < linksTableModel.getRowCount()) {
                final LinkTitleRelation removed = linksTableModel.remove(modelRow);
                LOG.debug("Link removed");
                debtComboModel.addElement(new LinkDebtItem(removed.debtId(), removed.debtTitle()));
            }
        });
        final TableColumn actionCol = linksTable.getColumnModel().getColumn(2);
        actionCol.setCellRenderer(actionButtons);
        actionCol.setCellEditor(actionButtons);
        actionCol.setPreferredWidth(52);
        actionCol.setMaxWidth(60);
        actionCol.setMinWidth(44);

        final JBScrollPane tableScroll = new JBScrollPane(linksTable);
        tableScroll.setPreferredSize(new Dimension(400, 160));

        return tableScroll;
    }

    public JPanel getPane() {
        return root;
    }

    public Map<String, Relationship> getLinks() {
        if (linksTableModel == null) {
            return Map.of();
        }

        return linksTableModel.getLinks();
    }
}
