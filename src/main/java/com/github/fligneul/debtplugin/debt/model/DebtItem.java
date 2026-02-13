package com.github.fligneul.debtplugin.debt.model;

import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class DebtItem {
    private final String id;
    private final String file;
    private final int line;
    private final String title;
    private final String description;
    private final String username;
    private final int wantedLevel;
    private final Complexity complexity;
    private final Status status;
    private final String priority;
    private final Risk risk;
    private final String targetVersion;
    private final String comment;
    private final int estimation;
    private final String jira;
    // Maven current module identifier (groupId:artifactId). Empty when unknown.
    private final String currentModule;
    // Links to other debts keyed by target debt id with a list of relationships
    private final Map<String, Relationship> links;

    // No-args constructor for serializers (e.g., Gson)
    public DebtItem() {
        this.id = UUID.randomUUID().toString();
        this.file = "";
        this.line = 0;
        this.title = "";
        this.description = "";
        this.username = "";
        this.wantedLevel = 3;
        this.complexity = Complexity.Medium;
        this.status = Status.Submitted;
        this.priority = "";
        this.risk = Risk.Medium;
        this.targetVersion = "";
        this.comment = "";
        this.estimation = 0;
        this.currentModule = "";
        this.links = new LinkedHashMap<>();
        this.jira = "";
    }

    private DebtItem(final Builder builder) {
        this.id = builder.id != null && !builder.id.isBlank() ? builder.id : UUID.randomUUID().toString();
        this.file = Objects.requireNonNull(builder.file, "file");
        this.line = builder.line;
        this.title = Objects.requireNonNull(builder.title, "title");
        this.description = Objects.requireNonNull(builder.description, "description");
        this.username = Objects.requireNonNull(builder.username, "username");
        this.wantedLevel = builder.wantedLevel;
        this.complexity = Objects.requireNonNull(builder.complexity, "complexity");
        this.status = Objects.requireNonNull(builder.status, "status");
        this.priority = Objects.requireNonNull(builder.priority, "priority");
        this.risk = Objects.requireNonNull(builder.risk, "risk");
        this.targetVersion = Objects.requireNonNull(builder.targetVersion, "targetVersion");
        this.comment = Objects.requireNonNull(builder.comment, "comment");
        this.estimation = builder.estimation;
        this.currentModule = builder.currentModule;
        this.links = builder.links != null ? new LinkedHashMap<>(builder.links) : new LinkedHashMap<>();
        this.jira = builder.jira == null || builder.jira.isBlank() ? "": builder.jira;
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder(this);
    }

    @NotNull
    public String getId() {
        return id;
    }

    @NotNull
    public Map<String, Relationship> getLinks() {
        return new LinkedHashMap<>(links);
    }

    @NotNull
    public String getFile() {
        return file;
    }

    public int getLine() {
        return line;
    }

    @NotNull
    public String getTitle() {
        return title;
    }

    @NotNull
    public String getDescription() {
        return description;
    }

    @NotNull
    public String getUsername() {
        return username;
    }

    public int getWantedLevel() {
        return wantedLevel;
    }

    @NotNull
    public Complexity getComplexity() {
        return complexity;
    }

    @NotNull
    public Status getStatus() {
        return status;
    }

    @NotNull
    public String getPriority() {
        return priority;
    }

    @NotNull
    public Risk getRisk() {
        return risk;
    }

    @NotNull
    public String getTargetVersion() {
        return targetVersion;
    }

    @NotNull
    public String getComment() {
        return comment;
    }

    public int getEstimation() {
        return estimation;
    }

    public String getCurrentModule() {
        return currentModule;
    }

    public String getJira() {
        return jira;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DebtItem debtItem = (DebtItem) o;
        return line == debtItem.line
                && wantedLevel == debtItem.wantedLevel
                && estimation == debtItem.estimation
                && Objects.equals(id, debtItem.id)
                && Objects.equals(file, debtItem.file)
                && Objects.equals(title, debtItem.title)
                && Objects.equals(description, debtItem.description)
                && Objects.equals(username, debtItem.username)
                && complexity == debtItem.complexity
                && status == debtItem.status
                && priority == debtItem.priority
                && risk == debtItem.risk
                && Objects.equals(targetVersion, debtItem.targetVersion)
                && Objects.equals(comment, debtItem.comment)
                && Objects.equals(currentModule, debtItem.currentModule)
                && Objects.equals(links, debtItem.links)
                && Objects.equals(jira, debtItem.jira);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, file, line, title, description, username, wantedLevel, complexity, status, priority, risk, targetVersion, comment, estimation, currentModule, links, jira);
    }

    @Override
    public String toString() {
        return "DebtItem{" +
                "id='" + id + '\'' +
                ", file='" + file + '\'' +
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
                ", estimation=" + estimation +
                ", currentModule='" + currentModule + '\'' +
                ", links=" + links +
                ", jira=" + jira +
                '}';
    }

    public static class Builder {
        private String id;
        private String file;
        private int line;
        private String title;
        private String description;
        private String username;
        private int wantedLevel;
        private Complexity complexity;
        private Status status;
        private String priority;
        private Risk risk;
        private String targetVersion;
        private String comment;
        private int estimation;
        private String currentModule;
        private Map<String, Relationship> links;
        private String jira;

        public Builder() {
        }

        public Builder(DebtItem item) {
            this.id = item.id;
            this.file = item.file;
            this.line = item.line;
            this.title = item.title;
            this.description = item.description;
            this.username = item.username;
            this.wantedLevel = item.wantedLevel;
            this.complexity = item.complexity;
            this.status = item.status;
            this.priority = item.priority;
            this.risk = item.risk;
            this.targetVersion = item.targetVersion;
            this.comment = item.comment;
            this.estimation = item.estimation;
            this.currentModule = item.currentModule;
            this.links = item.links;
            this.jira = item.jira;
        }

        public Builder withId(final String id) {
            this.id = id;
            return this;
        }

        public Builder withLinks(final Map<String, Relationship> links) {
            this.links = links;
            return this;
        }

        public Builder withFile(final String file) {
            this.file = file;
            return this;
        }

        public Builder withLine(final int line) {
            this.line = line;
            return this;
        }

        public Builder withTitle(final String title) {
            this.title = title;
            return this;
        }

        public Builder withDescription(final String description) {
            this.description = description;
            return this;
        }

        public Builder withUsername(final String username) {
            this.username = username;
            return this;
        }

        public Builder withWantedLevel(final int wantedLevel) {
            this.wantedLevel = wantedLevel;
            return this;
        }

        public Builder withComplexity(final Complexity complexity) {
            this.complexity = complexity;
            return this;
        }

        public Builder withStatus(final Status status) {
            this.status = status;
            return this;
        }

        public Builder withPriority(final String priority) {
            this.priority = priority;
            return this;
        }

        public Builder withRisk(final Risk risk) {
            this.risk = risk;
            return this;
        }

        public Builder withTargetVersion(final String targetVersion) {
            this.targetVersion = targetVersion;
            return this;
        }

        public Builder withComment(final String comment) {
            this.comment = comment;
            return this;
        }

        public Builder withEstimation(final int estimation) {
            this.estimation = estimation;
            return this;
        }

        public Builder withCurrentModule(final String currentModule) {
            this.currentModule = currentModule;
            return this;
        }

        public Builder withJira(final String jira) {
            this.jira = jira;
            return this;
        }

        public DebtItem build() {
            return new DebtItem(this);
        }
    }
}
