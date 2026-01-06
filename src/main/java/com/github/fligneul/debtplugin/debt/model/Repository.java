package com.github.fligneul.debtplugin.debt.model;

import java.util.Objects;

public final class Repository {
    private final String repositoryAbsolutePath;
    private final String repositoryName;
    private String jsonPath;

    public Repository(String repositoryAbsolutePath, String repositoryName) {
        this.repositoryAbsolutePath = repositoryAbsolutePath;
        this.repositoryName = repositoryName;
    }

    public String getRepositoryAbsolutePath() {
        return repositoryAbsolutePath;
    }

    public String getRepositoryName() {
        return repositoryName;
    }

    public String getJsonPath() {
        return jsonPath;
    }

    public void setJsonPath(final String jsonPath) {
        this.jsonPath = jsonPath;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (Repository) obj;
        return Objects.equals(this.repositoryAbsolutePath, that.repositoryAbsolutePath) &&
                Objects.equals(this.repositoryName, that.repositoryName) &&
                Objects.equals(this.jsonPath, that.jsonPath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(repositoryAbsolutePath, repositoryName, jsonPath);
    }

    @Override
    public String toString() {
        return "Repository[" +
                "absolutePath=" + repositoryAbsolutePath + ", " +
                "name=" + repositoryName + ", " +
                "jsonPath=" + jsonPath + ']';
    }

}
