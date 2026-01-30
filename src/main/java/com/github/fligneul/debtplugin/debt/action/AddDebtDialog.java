package com.github.fligneul.debtplugin.debt.action;

import com.github.fligneul.debtplugin.debt.action.links.LinksComponent;
import com.github.fligneul.debtplugin.debt.model.Complexity;
import com.github.fligneul.debtplugin.debt.model.DebtItem;
import com.github.fligneul.debtplugin.debt.model.Priority;
import com.github.fligneul.debtplugin.debt.model.Relationship;
import com.github.fligneul.debtplugin.debt.model.Repository;
import com.github.fligneul.debtplugin.debt.model.Risk;
import com.github.fligneul.debtplugin.debt.model.Status;
import com.github.fligneul.debtplugin.debt.service.DebtService;
import com.github.fligneul.debtplugin.debt.service.RepositoriesService;
import com.github.fligneul.debtplugin.debt.toolkit.SwingComponentHelper;
import com.github.fligneul.debtplugin.debt.toolwindow.DebtProviderService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.components.JBTextField;
import org.jetbrains.annotations.Nullable;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import java.io.File;
import java.util.Map;
import java.util.UUID;

public class AddDebtDialog extends DialogWrapper {
    private final Project project;
    private final DebtProviderService debtProviderService;
    private final TextFieldWithBrowseButton fileField = new TextFieldWithBrowseButton();
    private final JSpinner lineSpinner = new JSpinner(new SpinnerNumberModel(1, 1, Integer.MAX_VALUE, 1));
    private final JBTextField titleField = new JBTextField();
    private final JBTextArea descriptionArea = new JBTextArea(4, 40);
    private final JBTextArea commentArea = new JBTextArea(4, 40);
    private final JSpinner wantedLevelSpinner = new JSpinner(new SpinnerNumberModel(3, 1, 5, 1));
    private final JComboBox<Complexity> complexityComboBox = new JComboBox<>(Complexity.values());
    private final JComboBox<Status> statusComboBox = new JComboBox<>(Status.values());
    private final JComboBox<Priority> priorityComboBox = new JComboBox<>(Priority.values());
    private final JComboBox<Risk> riskComboBox = new JComboBox<>(Risk.values());
    private final JBTextField targetVersionField = new JBTextField();
    private final JSpinner estimationSpinner = new JSpinner(new SpinnerNumberModel(0, 0, Integer.MAX_VALUE, 1));
    private final JComboBox<Repository> repositoryComboBox = new JComboBox<>();
    private final DebtService debtService;

    // Whether the dialog is used to edit an existing item (true) or add a new one (false)
    private final boolean isEdit;

    private final String id;
    private String filePath = "";
    private int line = 1;
    private String titleText = "";
    private String description = "";
    private String comment = "";
    private int wantedLevel = 3;
    private Complexity complexity = Complexity.Medium;
    private Status status = Status.Submitted;
    private Priority priority = Priority.Medium;
    private Risk risk = Risk.Medium;
    private String targetVersion = "";
    private int estimation = 0;
    private Map<String, Relationship> links;
    private Repository selectedRepository;

    private LinksComponent linksComponent;

    public AddDebtDialog(Project project, String initialFilePath, int initialLine) {
        super(project, true);
        this.project = project;
        this.debtProviderService = project.getService(DebtProviderService.class);
        this.debtService = project.getService(DebtService.class);
        setTitle("Add New Debt");
        setResizable(true);
        this.id = UUID.randomUUID().toString();
        this.isEdit = false;
        this.filePath = initialFilePath != null ? initialFilePath : "";
        this.line = Math.max(1, initialLine);
        this.links = Map.of();
        init();
        // Push initial values into UI controls
        fileField.setText(this.filePath);
        lineSpinner.setValue(this.line);
        statusComboBox.setSelectedItem(this.status);
        priorityComboBox.setSelectedItem(this.priority);
        riskComboBox.setSelectedItem(this.risk);
        targetVersionField.setText(this.targetVersion);
        commentArea.setText(this.comment);
        estimationSpinner.setValue(0);
    }

    public AddDebtDialog(Project project, DebtItem item) {
        super(project, true);
        this.project = project;
        this.debtProviderService = project.getService(DebtProviderService.class);
        this.debtService = project.getService(DebtService.class);
        setTitle("Edit Debt");
        setResizable(true);
        this.isEdit = true;
        // Pre-fill backing fields so getters have values even if user doesn't change inputs
        this.id = item.getId();
        this.filePath = item.getFile();
        this.line = item.getLine();
        this.titleText = item.getTitle();
        this.description = item.getDescription();
        this.comment = item.getComment();
        this.wantedLevel = item.getWantedLevel();
        this.complexity = item.getComplexity();
        this.status = item.getStatus();
        this.priority = item.getPriority();
        this.risk = item.getRisk();
        this.targetVersion = item.getTargetVersion();
        this.estimation = item.getEstimation();
        this.links = item.getLinks();
        init();
        // After components are created in init(), push values into UI controls
        fileField.setText(item.getFile());
        lineSpinner.setValue(item.getLine());
        titleField.setText(item.getTitle());
        descriptionArea.setText(item.getDescription());
        commentArea.setText(item.getComment());
        wantedLevelSpinner.setValue(item.getWantedLevel());
        complexityComboBox.setSelectedItem(item.getComplexity());
        statusComboBox.setSelectedItem(item.getStatus());
        priorityComboBox.setSelectedItem(item.getPriority());
        riskComboBox.setSelectedItem(item.getRisk());
        targetVersionField.setText(item.getTargetVersion());
        estimationSpinner.setValue(item.getEstimation());
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        // Populate repository combobox with repositories from RepositoriesService
        RepositoriesService repositoriesService = project.getService(RepositoriesService.class);
        repositoryComboBox.removeAllItems();
        for (Repository repo : repositoriesService.getRepositories()) {
            repositoryComboBox.addItem(repo);
        }
        // Set custom renderer to display repository name
        repositoryComboBox.setRenderer((list, value, index, isSelected, cellHasFocus) -> {
            JLabel label = new JLabel();
            if (value != null) {
                label.setText(value.getRepositoryName());
            }
            if (isSelected) {
                label.setBackground(list.getSelectionBackground());
                label.setForeground(list.getSelectionForeground());
                label.setOpaque(true);
            }
            return label;
        });

        // Repository combobox row - visible only when file is null or blank
        final JPanel repositoryRow = SwingComponentHelper.labeled("Repository:", repositoryComboBox);
        repositoryRow.setVisible(filePath == null || filePath.isBlank());
        panel.add(repositoryRow);

        // File picker using Swing's JFileChooser to open the OS file dialog
        fileField.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            if (filePath != null && !filePath.isBlank()) {
                chooser.setSelectedFile(new File(filePath));
            }
            int result = chooser.showOpenDialog(panel);
            if (result == JFileChooser.APPROVE_OPTION && chooser.getSelectedFile() != null) {
                String chosenPath = chooser.getSelectedFile().getAbsolutePath();
                fileField.setText(chosenPath);
                // Update repository combobox visibility when file changes
                String currentFile = fileField.getText();
                repositoryRow.setVisible(currentFile == null || currentFile.isBlank());
                panel.revalidate();
                panel.repaint();
            }
        });
        
        // Add document listener to file field to update repository combobox visibility
        fileField.getTextField().getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                updateRepositoryVisibility();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                updateRepositoryVisibility();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                updateRepositoryVisibility();
            }

            private void updateRepositoryVisibility() {
                String currentFile = fileField.getText();
                repositoryRow.setVisible(currentFile == null || currentFile.isBlank());
                panel.revalidate();
                panel.repaint();
            }
        });

        final JPanel fileRow = SwingComponentHelper.labeled("File:", fileField);
        fileRow.setVisible(isEdit);
        panel.add(fileRow);

        // Line number
        final JPanel lineRow = SwingComponentHelper.labeled("Line:", lineSpinner);
        lineRow.setVisible(isEdit);
        panel.add(lineRow);

        panel.add(SwingComponentHelper.labeled("Title:", titleField));

        JScrollPane descriptionScroll = new JScrollPane(descriptionArea);
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);
        panel.add(SwingComponentHelper.labeled("Description:", descriptionScroll));

        panel.add(SwingComponentHelper.labeled("Wanted Level (1-5):", wantedLevelSpinner));
        panel.add(SwingComponentHelper.labeled("Complexity:", complexityComboBox));


        // Row panels to allow toggling visibility
        final JPanel statusRow = SwingComponentHelper.labeled("Status:", statusComboBox);
        statusRow.setVisible(isEdit);
        panel.add(statusRow);

        final JPanel priorityRow = SwingComponentHelper.labeled("Priority:", priorityComboBox);
        priorityRow.setVisible(isEdit);
        panel.add(priorityRow);

        final JPanel riskRow = SwingComponentHelper.labeled("Risk:", riskComboBox);
        riskRow.setVisible(isEdit);
        panel.add(riskRow);

        final JPanel targetVersionRow = SwingComponentHelper.labeled("Target Version:", targetVersionField);
        targetVersionRow.setVisible(isEdit);
        panel.add(targetVersionRow);

        JScrollPane commentScroll = new JScrollPane(commentArea);
        commentArea.setLineWrap(true);
        commentArea.setWrapStyleWord(true);
        final JPanel commentRow = SwingComponentHelper.labeled("Comment:", commentScroll);
        commentRow.setVisible(isEdit);
        panel.add(commentRow);
        panel.add(Box.createVerticalStrut(4));

        // Estimation
        panel.add(SwingComponentHelper.labeled("Estimation:", estimationSpinner));
        panel.add(Box.createVerticalStrut(4));

        linksComponent = new LinksComponent(debtService, debtProviderService, links, id);
        JPanel linksPanel = linksComponent.getPane();
        linksPanel.setVisible(isEdit);
        panel.add(linksPanel);

        return new JBScrollPane(panel);
    }

    @Override
    protected void doOKAction() {
        this.filePath = fileField.getText() != null ? fileField.getText().trim() : "";
        Object ln = lineSpinner.getValue();
        this.line = (ln instanceof Number) ? Math.max(1, ((Number) ln).intValue()) : 1;
        Object estimation = estimationSpinner.getValue();
        this.estimation = (estimation instanceof Number) ? Math.max(0, ((Number) estimation).intValue()) : 0;
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
        this.links = linksComponent == null
                ? Map.of()
                : linksComponent.getLinks();
        this.selectedRepository = (Repository) repositoryComboBox.getSelectedItem();

        super.doOKAction();
    }

    public String getId() {
        return id;
    }

    public String getFilePath() {
        return filePath;
    }

    public int getLine() {
        return line;
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

    public int getEstimation() {
        return estimation;
    }

    public Map<String, Relationship> getLinks() {
        return this.links;
    }

    public Repository getSelectedRepository() {
        return selectedRepository;
    }
}
