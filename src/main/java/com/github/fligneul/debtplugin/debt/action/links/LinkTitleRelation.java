package com.github.fligneul.debtplugin.debt.action.links;

import com.github.fligneul.debtplugin.debt.model.Relationship;

public record LinkTitleRelation(String debtId, String debtTitle, Relationship relationship) {
    public Object[] toObject() {
        return new Object[]{
                // debtId is not display to the user
                debtTitle, relationship
        };
    }
}
