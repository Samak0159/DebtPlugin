package com.github.fligneul.debtplugin.debt.action;

import com.github.fligneul.debtplugin.debt.model.Complexity;
import com.github.fligneul.debtplugin.debt.model.Priority;
import com.github.fligneul.debtplugin.debt.model.Risk;
import com.github.fligneul.debtplugin.debt.model.Status;
import com.github.fligneul.debtplugin.debt.model.DebtItem;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.components.JBTextField;
import org.jetbrains.annotations.Nullable;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

public class AddDebtDialog extends DialogWrapper {
    private final JBTextField titleField = new JBTextField();
    private final JBTextArea descriptionArea = new JBTextArea(4, 40);
    private final JBTextArea commentArea = new JBTextArea(4, 40);
    private final JSpinner wantedLevelSpinner = new JSpinner(new SpinnerNumberModel(3, 1, 5, 1));
    private final JComboBox<Complexity> complexityComboBox = new JComboBox<>(Complexity.values());
    private final JComboBox<Status> statusComboBox = new JComboBox<>(Status.values());
    private final JComboBox<Priority> priorityComboBox = new JComboBox<>(Priority.values());
    private final JComboBox<Risk> riskComboBox = new JComboBox<>(Risk.values());
    private final JBTextField targetVersionField = new JBTextField();

    private String titleText = "";
    private String description = "";
    private String comment = "";
    private int wantedLevel = 3;
    private Complexity complexity = Complexity.Medium;
    private Status status = Status.Submitted;
    private Priority priority = Priority.Medium;
    private Risk risk = Risk.Medium;
    private String targetVersion = "";

    public AddDebtDialog() {
        super(true);
        setTitle("Add New Debt");
        init();
    }

    public AddDebtDialog(DebtItem item) {
        super(true);
        setTitle("Edit Debt");
        // Pre-fill backing fields so getters have values even if user doesn't change inputs
        this.titleText = item.getTitle();
        this.description = item.getDescription();
        this.comment = item.getComment();
        this.wantedLevel = item.getWantedLevel();
        this.complexity = item.getComplexity();
        this.status = item.getStatus();
        this.priority = item.getPriority();
        this.risk = item.getRisk();
        this.targetVersion = item.getTargetVersion();
        init();
        // After components are created in init(), push values into UI controls
        titleField.setText(item.getTitle());
        descriptionArea.setText(item.getDescription());
        commentArea.setText(item.getComment());
        wantedLevelSpinner.setValue(item.getWantedLevel());
        complexityComboBox.setSelectedItem(item.getComplexity());
        statusComboBox.setSelectedItem(item.getStatus());
        priorityComboBox.setSelectedItem(item.getPriority());
        riskComboBox.setSelectedItem(item.getRisk());
        targetVersionField.setText(item.getTargetVersion());
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.add(labeled("Title:", titleField));
        JScrollPane descriptionScroll = new JScrollPane(descriptionArea);
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);
        panel.add(labeled("Description:", descriptionScroll));
        JScrollPane commentScroll = new JScrollPane(commentArea);
        commentArea.setLineWrap(true);
        commentArea.setWrapStyleWord(true);
        panel.add(labeled("Wanted Level (1-5):", wantedLevelSpinner));
        panel.add(labeled("Complexity:", complexityComboBox));
        panel.add(labeled("Status:", statusComboBox));
        panel.add(labeled("Priority:", priorityComboBox));
        panel.add(labeled("Risk:", riskComboBox));
        panel.add(labeled("Target Version:", targetVersionField));
        panel.add(labeled("Comment:", commentScroll));
        return panel;
    }

    private static JPanel labeled(String label, JComponent comp) {
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.add(new JLabel(label));
        row.add(Box.createHorizontalStrut(8));
        row.add(comp);
        return row;
    }

    @Override
    protected void doOKAction() {
        this.titleText = titleField.getText();
        this.description = descriptionArea.getText();
        this.comment = commentArea.getText();
        Object val = wantedLevelSpinner.getValue();
        this.wantedLevel = (val instanceof Number) ? ((Number) val).intValue() : 3;
        this.complexity = (Complexity) complexityComboBox.getSelectedItem();
        this.status = (Status) statusComboBox.getSelectedItem();
        this.priority = (Priority) priorityComboBox.getSelectedItem();
        this.risk = (Risk) riskComboBox.getSelectedItem();
        this.targetVersion = targetVersionField.getText();
        super.doOKAction();
    }

    public String getTitleText() {
        return titleText;
    }

    public String getDescription() {
        return description;
    }

    public String getComment() {
        return comment;
    }

    public int getWantedLevel() {
        return wantedLevel;
    }

    public Complexity getComplexity() {
        return complexity;
    }

    public Status getStatus() {
        return status;
    }

    public Priority getPriority() {
        return priority;
    }

    public Risk getRisk() {
        return risk;
    }

    public String getTargetVersion() {
        return targetVersion;
    }
}
