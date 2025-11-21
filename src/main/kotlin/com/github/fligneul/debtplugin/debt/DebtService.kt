package com.github.fligneul.debtplugin.debt

import com.github.fligneul.debtplugin.settings.DebtSettings
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import java.io.File

@Service(Service.Level.PROJECT)
class DebtService(private val project: Project) {

    private class DebtItemDeserializer : JsonDeserializer<DebtItem> {
        override fun deserialize(json: JsonElement, typeOfT: java.lang.reflect.Type, context: JsonDeserializationContext): DebtItem {
            val obj: JsonObject = json.asJsonObject
            val file = obj.get("file")?.asString ?: ""
            val line = obj.get("line")?.asInt ?: 1
            val description = obj.get("description")?.asString ?: ""
            val statusStr = obj.get("status")?.asString
            val status = try {
                if (statusStr.isNullOrBlank()) Status.Submitted else Status.valueOf(statusStr)
            } catch (_: Exception) {
                Status.Submitted
            }
            val priorityStr = obj.get("priority")?.asString
            val priority = try {
                if (priorityStr.isNullOrBlank()) Priority.Medium else Priority.valueOf(priorityStr)
            } catch (_: Exception) {
                Priority.Medium
            }
            val comment = obj.get("comment")?.asString ?: ""
            val username = obj.get("username")?.asString ?: ""
            return DebtItem(
                file = file,
                line = line,
                description = description,
                status = status,
                priority = priority,
                comment = comment,
                username = username
            )
        }
    }

    private val gson: Gson = GsonBuilder()
        .registerTypeAdapter(DebtItem::class.java, DebtItemDeserializer())
        .create()
    private val settings = project.service<DebtSettings>()
    val debtFile by lazy {
        File(project.basePath, settings.state.debtFilePath)
    }
    private val debts = mutableListOf<DebtItem>()

    init {
        loadDebts()
    }

    fun add(debtItem: DebtItem) {
        debts.add(debtItem)
        saveDebts()
        refreshHighlighting()
    }

    fun remove(debtItem: DebtItem) {
        debts.remove(debtItem)
        saveDebts()
        refreshHighlighting()
    }

    fun update(oldDebtItem: DebtItem, newDebtItem: DebtItem) {
        val index = debts.indexOf(oldDebtItem)
        if (index != -1) {
            debts[index] = newDebtItem
            saveDebts()
            refreshHighlighting()
        }
    }

    fun all(): List<DebtItem> {
        return debts.toList()
    }

    /**
     * Replace all occurrences of [oldUsername] with [newUsername] across existing debts.
     * Skips if [oldUsername] is blank or equals [newUsername].
     */
    fun migrateUsername(oldUsername: String, newUsername: String) {
        if (oldUsername.isBlank() || oldUsername == newUsername) return
        var changed = false
        for (i in debts.indices) {
            val d = debts[i]
            if (d.username == oldUsername) {
                debts[i] = d.copy(username = newUsername)
                changed = true
            }
        }
        if (changed) {
            saveDebts()
            refreshHighlighting()
        }
    }

    private fun loadDebts() {
        if (debtFile.exists()) {
            val type = object : TypeToken<List<DebtItem>>() {}.type
            debts.clear()
            debts.addAll(gson.fromJson(debtFile.readText(), type))
        }
    }

    private fun saveDebts() {
        debtFile.writeText(gson.toJson(debts))
    }

    private fun refreshHighlighting() {
        // Trigger re-highlighting so gutter markers update after debt changes
        DaemonCodeAnalyzer.getInstance(project).restart()
    }
}
