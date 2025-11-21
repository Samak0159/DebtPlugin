package com.github.fligneul.debtplugin.debt

import com.github.fligneul.debtplugin.settings.DebtSettings
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import java.io.File

@Service(Service.Level.PROJECT)
class DebtService(private val project: Project) {

    private val gson = Gson()
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
