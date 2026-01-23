package com.github.fligneul.debtplugin.debt.action.links;

public record LinkDebtItem(String id, String debtName) {
    @Override
    public String toString() {
        return debtName;
    }
}
