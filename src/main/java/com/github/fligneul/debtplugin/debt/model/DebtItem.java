package com.github.fligneul.debtplugin.debt.model;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class DebtItem {
    private String file;
    private int line;
    private String title;
    private String description;
    private String username;
    private int wantedLevel;
    private Complexity complexity;
    private Status status;
    private Priority priority;
    private Risk risk;
    private String targetVersion;
    private String comment;
    // Maven current module identifier (groupId:artifactId). Empty when unknown.
    private String currentModule;

    // No-args constructor for serializers (e.g., Gson)
    public DebtItem() {
        this.file = "";
        this.line = 0;
        this.title = "";
        this.description = "";
        this.username = "";
        this.wantedLevel = 3;
        this.complexity = Complexity.Medium;
        this.status = Status.Submitted;
        this.priority = Priority.Medium;
        this.risk = Risk.Medium;
        this.targetVersion = "";
        this.comment = "";
        this.currentModule = "";
    }

    public DebtItem(
            @NotNull String file,
            int line,
            @NotNull String title,
            @NotNull String description,
            @NotNull String username,
            int wantedLevel,
            @NotNull Complexity complexity,
            @NotNull Status status,
            @NotNull Priority priority,
            @NotNull Risk risk,
            @NotNull String targetVersion,
            @NotNull String comment
    ) {
        this.file = Objects.requireNonNull(file, "file");
        this.line = line;
        this.title = Objects.requireNonNull(title, "title");
        this.description = Objects.requireNonNull(description, "description");
        this.username = Objects.requireNonNull(username, "username");
        this.wantedLevel = wantedLevel;
        this.complexity = Objects.requireNonNull(complexity, "complexity");
        this.status = Objects.requireNonNull(status, "status");
        this.priority = Objects.requireNonNull(priority, "priority");
        this.risk = Objects.requireNonNull(risk, "risk");
        this.targetVersion = Objects.requireNonNull(targetVersion, "targetVersion");
        this.comment = Objects.requireNonNull(comment, "comment");
    }

    @NotNull public String getFile() { return file; }
    public void setFile(@NotNull String file) { this.file = file; }

    public int getLine() { return line; }
    public void setLine(int line) { this.line = line; }

    @NotNull public String getTitle() { return title; }
    public void setTitle(@NotNull String title) { this.title = title; }

    @NotNull public String getDescription() { return description; }
    public void setDescription(@NotNull String description) { this.description = description; }

    @NotNull public String getUsername() { return username; }
    public void setUsername(@NotNull String username) { this.username = username; }

    public int getWantedLevel() { return wantedLevel; }
    public void setWantedLevel(int wantedLevel) { this.wantedLevel = wantedLevel; }

    @NotNull public Complexity getComplexity() { return complexity; }
    public void setComplexity(@NotNull Complexity complexity) { this.complexity = complexity; }

    @NotNull public Status getStatus() { return status; }
    public void setStatus(@NotNull Status status) { this.status = status; }

    @NotNull public Priority getPriority() { return priority; }
    public void setPriority(@NotNull Priority priority) { this.priority = priority; }

    @NotNull public Risk getRisk() { return risk; }
    public void setRisk(@NotNull Risk risk) { this.risk = risk; }

    @NotNull public String getTargetVersion() { return targetVersion; }
    public void setTargetVersion(@NotNull String targetVersion) { this.targetVersion = targetVersion; }

    @NotNull public String getComment() { return comment; }
    public void setComment(@NotNull String comment) { this.comment = comment; }

    @NotNull public String getCurrentModule() { return currentModule; }
    public void setCurrentModule(@NotNull String moduleParent) { this.currentModule = moduleParent; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DebtItem debtItem = (DebtItem) o;
        return line == debtItem.line && wantedLevel == debtItem.wantedLevel &&
                Objects.equals(file, debtItem.file) && Objects.equals(title, debtItem.title) &&
                Objects.equals(description, debtItem.description) && Objects.equals(username, debtItem.username) &&
                complexity == debtItem.complexity && status == debtItem.status && priority == debtItem.priority &&
                risk == debtItem.risk && Objects.equals(targetVersion, debtItem.targetVersion) && Objects.equals(comment, debtItem.comment)
                && Objects.equals(currentModule, debtItem.currentModule);
    }

    @Override
    public int hashCode() {
        return Objects.hash(file, line, title, description, username, wantedLevel, complexity, status, priority, risk, targetVersion, comment, currentModule);
    }

    @Override
    public String toString() {
        return "DebtItem{" +
                "file='" + file + '\'' +
                ", line=" + line +
                ", title='" + title + '\'' +
                ", description='" + description + '\'' +
                ", username='" + username + '\'' +
                ", wantedLevel=" + wantedLevel +
                ", complexity=" + complexity +
                ", status=" + status +
                ", priority=" + priority +
                ", risk=" + risk +
                ", targetVersion='" + targetVersion + '\'' +
                ", comment='" + comment + '\'' +
                ", currentModule='" + currentModule + '\'' +
                '}';
    }
}
