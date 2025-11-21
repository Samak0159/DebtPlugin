package com.github.fligneul.debtplugin.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.messages.Topic
import java.util.UUID

interface DebtSettingsListener {
    fun settingsChanged(settings: DebtSettings.State)
}

@State(
    name = "com.github.fligneul.debtplugin.settings.DebtSettings",
    storages = [Storage("debt.xml")]
)
@Service(Service.Level.PROJECT)
class DebtSettings : PersistentStateComponent<DebtSettings.State> {

    companion object {
        val TOPIC = Topic.create("Debt Settings Changed", DebtSettingsListener::class.java)
    }

    data class State(
        var debtFilePath: String = "debt.json",
        var username: String = ""
    )

    private var myState = State()

    override fun getState(): State {
        // Ensure username is initialized before persisting state
        if (myState.username.isBlank()) {
            myState.username = UUID.randomUUID().toString()
        }
        return myState
    }

    override fun loadState(state: State) {
        myState = state
        if (myState.username.isBlank()) {
            myState.username = UUID.randomUUID().toString()
        }
    }

    fun getOrInitUsername(): String {
        if (myState.username.isBlank()) {
            myState.username = UUID.randomUUID().toString()
        }
        return myState.username
    }
}
