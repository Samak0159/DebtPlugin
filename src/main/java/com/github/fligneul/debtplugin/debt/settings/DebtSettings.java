package com.github.fligneul.debtplugin.debt.settings;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@State(
        name = "com.github.fligneul.debtplugin.debt.settings.DebtSettings",
        storages = {@Storage("debt.xml")}
)
@Service(Service.Level.PROJECT)
public final class DebtSettings implements PersistentStateComponent<DebtSettings.State> {

    public static final Topic<DebtSettingsListener> TOPIC = Topic.create("Debt Settings Changed", DebtSettingsListener.class);
    public static final String DEFAULT_DEBT_FILE_PATH = "dev/debt.json";

    public static final class State {
        public String username = "";
        // Optional per-repository override for JSON path. Key: absolute repo root; Value: absolute path or path relative to repo root.
        public Map<String, String> repoDebtPaths = new HashMap<>();

        public State() {
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public Map<String, String> getRepoDebtPaths() {
            return repoDebtPaths;
        }

        public void setRepoDebtPaths(Map<String, String> repoDebtPaths) {
            this.repoDebtPaths = repoDebtPaths;
        }

        public String getDebtFilePath(Project project) {
            final String basePath = project.getBasePath();

            return Optional.ofNullable(getRepoDebtPaths().get(basePath))
                    .orElseGet(() -> {
                        final String windowsBasePath = basePath.replace("/", "\\");

                        return getRepoDebtPaths().get(windowsBasePath);
                    });
        }
    }

    private State myState = new State();

    @Override
    public State getState() {
        if (myState.username == null || myState.username.isBlank()) {
            myState.username = UUID.randomUUID().toString();
        }
        if (myState.repoDebtPaths == null) {
            myState.repoDebtPaths = new HashMap<>();
        }
        return myState;
    }

    @Override
    public void loadState(@NotNull State state) {
        this.myState = state;
        if (myState.username == null || myState.username.isBlank()) {
            myState.username = UUID.randomUUID().toString();
        }
        if (myState.repoDebtPaths == null) myState.repoDebtPaths = new HashMap<>();
    }

    public String getOrInitUsername() {
        if (myState.username == null || myState.username.isBlank()) {
            myState.username = UUID.randomUUID().toString();
        }
        return myState.username;
    }
}
