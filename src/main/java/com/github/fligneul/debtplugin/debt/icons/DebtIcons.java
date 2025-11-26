package com.github.fligneul.debtplugin.debt.icons;

import com.intellij.openapi.util.IconLoader;

import javax.swing.Icon;

/**
 * Central place for plugin icons.
 */
public final class DebtIcons {
    private DebtIcons() {}

    public static final Icon TECHNICAL_DEBT = IconLoader.getIcon("/icons/technicalDebt.svg", DebtIcons.class);
}
