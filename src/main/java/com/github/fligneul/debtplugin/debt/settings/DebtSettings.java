package com.github.fligneul.debtplugin.debt.settings;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@State(
        name = "com.github.fligneul.debtplugin.debt.settings.DebtSettings",
        storages = {@Storage("debt.xml")}
)
@Service(Service.Level.PROJECT)
public final class DebtSettings implements PersistentStateComponent<DebtSettings.State> {

    public static final Topic<DebtSettingsListener> TOPIC = Topic.create("Debt Settings Changed", DebtSettingsListener.class);

    public static final class State {
        public String debtFilePath = "dev/debt.json";
        public String username = "";

        public State() {}

        public String getDebtFilePath() { return debtFilePath; }
        public void setDebtFilePath(String debtFilePath) { this.debtFilePath = debtFilePath; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
    }

    private State myState = new State();

    @Override
    public @Nullable State getState() {
        if (myState.username == null || myState.username.isBlank()) {
            myState.username = UUID.randomUUID().toString();
        }
        return myState;
    }

    @Override
    public void loadState(@NotNull State state) {
        this.myState = state;
        if (myState.username == null || myState.username.isBlank()) {
            myState.username = UUID.randomUUID().toString();
        }
    }

    public String getOrInitUsername() {
        if (myState.username == null || myState.username.isBlank()) {
            myState.username = UUID.randomUUID().toString();
        }
        return myState.username;
    }
}
