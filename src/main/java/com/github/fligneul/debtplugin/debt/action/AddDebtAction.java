package com.github.fligneul.debtplugin.debt.action;

import com.github.fligneul.debtplugin.debt.model.DebtItem;
import com.github.fligneul.debtplugin.debt.service.DebtService;
import com.github.fligneul.debtplugin.debt.settings.DebtSettings;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

public class AddDebtAction extends AnAction {
    private static final Logger LOG = Logger.getInstance(AddDebtAction.class);
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
            String storedPath = file.getPath();
            String basePath = project.getBasePath();
            if (basePath != null) {
                try {
                    Path abs = Paths.get(storedPath).toAbsolutePath().normalize();
                    Path base = Paths.get(basePath).toAbsolutePath().normalize();
                    if (abs.startsWith(base)) {
                        String before = storedPath;
                        storedPath = base.relativize(abs).toString().replace('\\', '/');
                        if (LOG.isDebugEnabled()) LOG.debug("Relativized path for add: '" + before + "' -> '" + storedPath + "'");
                    }
                } catch (Exception ex) {
                    LOG.error("Failed to relativize path for add. stored=" + storedPath + " basePath=" + basePath + " msg=" + ex.getMessage(), ex);
                }
            }
            DebtItem debtItem = new DebtItem(
                    storedPath,
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
                    dialog.getComment()
            );
            String currentModule = resolveCurrentModule(file.getPath(), project.getBasePath());
            if (currentModule != null) {
                debtItem.setCurrentModule(currentModule);
                if (LOG.isDebugEnabled()) LOG.debug("Resolved currentModule=" + currentModule + " for file=" + file.getPath());
            }
            LOG.info("Add debt confirmed: file=" + debtItem.getFile() + ":" + debtItem.getLine() +
                    " title=\"" + debtItem.getTitle() + "\"" +
                    " user=" + debtItem.getUsername());
            debtService.add(debtItem);
        } else {
            if (LOG.isDebugEnabled()) LOG.debug("Add debt dialog canceled");
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

    private static String resolveCurrentModule(String filePath, String projectBasePath) {
        try {
            Path start = Paths.get(filePath).toAbsolutePath();
            Path root = projectBasePath != null ? Paths.get(projectBasePath).toAbsolutePath() : start.getRoot();
            Path dir = start.getParent();
            while (dir != null) {
                File pom = dir.resolve("pom.xml").toFile();
                if (pom.exists()) {
                    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                    dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
                    dbf.setNamespaceAware(false);
                    DocumentBuilder db = dbf.newDocumentBuilder();
                    Document doc = db.parse(pom);
                    Element project = doc.getDocumentElement();
                    if (project != null) {
                        // Read only direct children to avoid picking values from <parent>
                        String artifactId = textOfDirectChild(project, "artifactId");
                        String groupId = textOfDirectChild(project, "groupId");
                        if (groupId == null || groupId.isBlank()) {
                            var parents = project.getElementsByTagName("parent");
                            if (parents.getLength() > 0) {
                                Element parent = (Element) parents.item(0);
                                String parentGroupId = textOfDirectChild(parent, "groupId");
                                if (parentGroupId != null && !parentGroupId.isBlank()) {
                                    groupId = parentGroupId;
                                }
                            }
                        }
                        if (artifactId != null && !artifactId.isBlank()) {
                            if (groupId != null && !groupId.isBlank()) {
                                return groupId + ":" + artifactId;
                            } else {
                                return artifactId;
                            }
                        }
                    }
                    // found a pom, stop searching upwards regardless
                    break;
                }
                if (dir.equals(root)) break;
                dir = dir.getParent();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static String textOfDirectChild(Element parent, String tag) {
        for (org.w3c.dom.Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                Element e = (Element) n;
                if (tag.equals(e.getTagName())) {
                    return e.getTextContent();
                }
            }
        }
        return null;
    }
}