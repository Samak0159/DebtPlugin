package com.github.fligneul.debtplugin.debt.service;

import com.github.fligneul.debtplugin.debt.model.Repository;
import com.github.fligneul.debtplugin.debt.settings.DebtSettings;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Centralized repository-related utilities.
 * Currently provides Maven roots discovery from .idea/misc.xml (originalFiles list).
 */
@Service(Service.Level.PROJECT)
public final class RepositoriesService {
    private static final Logger LOG = Logger.getInstance(RepositoriesService.class);

    private final Project project;
    // Cached repositories list, initialized from .idea/misc.xml and updatable by UI
    private final List<Repository> repositories = new ArrayList<>();

    public RepositoriesService(Project project) {
        this.project = project;
        try {
            // Initialize repositories list from misc.xml on service creation
            refreshFromMisc();
            if (LOG.isDebugEnabled()) LOG.debug("RepositoriesService initialized with roots=" + repositories);
        } catch (Throwable t) {
            LOG.warn("RepositoriesService initialization failed: " + t.getMessage(), t);
        }
    }

    private List<Repository> convertRootPathsToRepositories(final List<String> roots) {
        return roots.stream()
                .map(root -> {
                    final String repositoryName = getRepositoryName(root);
                    return new Repository(root, repositoryName, DebtSettings.DEFAULT_DEBT_FILE_PATH);
                })
                .toList();
    }

    private String getRepositoryName(final String absolutePath) {
        return new File(absolutePath).getName();
    }

    /**
     * Read .idea/vcs.xml and extract Maven root folders from
     * component[name="VcsDirectoryMappings"] > mapping[name="directory"]
     * If the file is missing or cannot be parsed, an empty list is returned.
     */
    private List<String> getMavenOriginalRootFoldersFromIdeaVsc() {
        try {
            String basePath = project.getBasePath();
            if (basePath == null || basePath.isBlank()) return Collections.emptyList();
            final File misc = new File(basePath, ".idea/vcs.xml");
            if (!misc.exists()) {
                LOG.error("file vcs do not exist ? path :" + misc.getAbsolutePath());
                return Collections.emptyList();
            }

            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            try {
                dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            } catch (Throwable ignore) {
            }
            dbf.setNamespaceAware(false);
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(misc);

            NodeList components = doc.getElementsByTagName("component");
            LinkedHashSet<String> roots = new LinkedHashSet<>();
            for (int i = 0; i < components.getLength(); i++) {
                Element comp = (Element) components.item(i);
                String name = comp.getAttribute("name");
                if (!"VcsDirectoryMappings".equals(name)) continue;
                // Find option[name="originalFiles"]
                final NodeList mappings = comp.getElementsByTagName("mapping");
                for (int j = 0; j < mappings.getLength(); j++) {
                    Element opt = (Element) mappings.item(j);
                    final String directory = opt.getAttribute("directory");

                    final Path dir;
                    if (directory.isBlank()) {
                        dir = Paths.get(basePath);
                    } else {
                        final String resolved = resolveIdeaPathVariable(directory, basePath);
                        dir = Paths.get(resolved).toAbsolutePath().normalize();
                    }
                    roots.add(dir.toString());
                }
            }
            return new ArrayList<>(roots);
        } catch (Exception e) {
            LOG.error("RepositoriesService: failed to parse .idea/misc.xml: " + e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Return the current repositories list snapshot.
     */
    public List<Repository> getRepositories() {
        synchronized (repositories) {
            if (repositories.isEmpty()) {
                refreshFromMisc();
            }
            return Collections.unmodifiableList(repositories);
        }
    }

    /**
     * Replace current repositories list with provided roots. Intended to be called by UI when
     * the repositories table is rebuilt/edited so that other components can consume a consistent list.
     */
    private void setRepositories(List<Repository> roots) {
        synchronized (repositories) {
            repositories.clear();
            if (roots != null) repositories.addAll(roots);
        }
        if (LOG.isDebugEnabled()) LOG.debug("RepositoriesService.setRepositories -> " + repositories);
    }

    /**
     * Refresh the repositories list by re-reading .idea/misc.xml.
     *
     * @return
     */
    public void refreshFromMisc() {
        List<String> roots = getMavenOriginalRootFoldersFromIdeaVsc();
        setRepositories(convertRootPathsToRepositories(roots));
    }

    public void refreshAndLoadDebts() {
        refreshFromMisc();

        final DebtService debtService = project.getService(DebtService.class);
        debtService.loadDebts();
    }

    private static String resolveIdeaPathVariable(String value, String basePath) {
        String v = value == null ? "" : value.trim();
        // Strip file:// prefix if present
        if (v.startsWith("file://")) v = v.substring("file://".length());
        // Replace $PROJECT_DIR$ placeholder
        v = v.replace("$PROJECT_DIR$", basePath != null ? basePath : "");
        // Normalize slashes
        v = v.replace('\\', '/');
        // If still not absolute, resolve against basePath
        try {
            Path p = Paths.get(v);
            if (!p.isAbsolute()) {
                if (basePath != null && !basePath.isBlank()) {
                    return Paths.get(basePath).resolve(v).normalize().toString();
                }
            }
            return p.toAbsolutePath().normalize().toString();
        } catch (Exception e) {
            return v;
        }
    }
}
