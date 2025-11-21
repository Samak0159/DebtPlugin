package com.github.fligneul.debtplugin.debt

enum class Status {
    Submitted,
    ToAnalyze,
    Accepted,
    Rejected,
    Fixed
}

enum class Priority {
    Low,
    Medium,
    High
}

data class DebtItem(
    val file: String,
    val line: Int,
    val description: String,
    val status: Status = Status.Submitted,
    val priority: Priority = Priority.Medium,
    val comment: String = "",
    val username: String = ""
)
