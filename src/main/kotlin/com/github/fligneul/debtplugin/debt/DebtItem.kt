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

enum class Complexity {
    Easy,
    Medium,
    Hard
}

enum class Risk {
    Low,
    Medium,
    High
}

// Note: new fields default to sensible values for backward compatibility
// title: "", wantedLevel: 3, complexity: Medium, risk: Medium, targetVersion: ""

data class DebtItem(
    val file: String,
    val line: Int,
    val title: String = "",
    val description: String,
    val username: String = "",
    val wantedLevel: Int = 3,
    val complexity: Complexity = Complexity.Medium,
    val status: Status = Status.Submitted,
    val priority: Priority = Priority.Medium,
    val risk: Risk = Risk.Medium,
    val targetVersion: String = "",
    val comment: String = ""
)
