package com.github.fligneul.debtplugin.debt.model;

public enum Field {
    FILE(false, false),
    LINE(false, false),
    TITLE(true, true),
    DESCRIPTION(true, true),
    WANTEDLEVEL(false, true),
    COMPLEXITY(false, true),
    STATUS(false, false),
    PRIORITY(false, false),
    RISK(false, false),
    TARGET_VERSION(false, false),
    COMMENT(false, false),
    ESTIMATION(false, false),
    JIRA(false, false),
    LINKS(false, false),
    TYPE(false, false);

    private final boolean isMandatory;
    private final boolean defaultVisibility;

    Field(final boolean isMandatory, final boolean defaultVisibility) {
        this.isMandatory = isMandatory;
        this.defaultVisibility = defaultVisibility;
    }

    public boolean isMandatory() {
        return isMandatory;
    }

    public boolean isDefaultVisibilty() {
        return defaultVisibility;
    }
}
