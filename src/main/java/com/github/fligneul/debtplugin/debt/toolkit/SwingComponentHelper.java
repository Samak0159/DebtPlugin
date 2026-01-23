package com.github.fligneul.debtplugin.debt.toolkit;

import com.intellij.ui.components.JBTextArea;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import java.awt.Dimension;

public class SwingComponentHelper {
    private static final int LABEL_COL_WIDTH = 160;

    public static JPanel labeled(String label, JComponent comp) {
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));

        JLabel jLabel = new JLabel(label);
        jLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        // Fix the label column width so all fields align nicely
        Dimension labelSize = jLabel.getPreferredSize();
        labelSize = new Dimension(LABEL_COL_WIDTH, labelSize.height);
        jLabel.setPreferredSize(labelSize);
        jLabel.setMinimumSize(labelSize);
        jLabel.setMaximumSize(new Dimension(LABEL_COL_WIDTH, Integer.MAX_VALUE));

        // Let input control stretch to fill remaining width; allow text areas (inside JScrollPane) to grow vertically
        Dimension compPref = comp.getPreferredSize();
        boolean isScrollableText = (comp instanceof JScrollPane) || (comp instanceof JBTextArea);
        if (isScrollableText) {
            comp.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        } else {
            comp.setMaximumSize(new Dimension(Integer.MAX_VALUE, compPref != null ? compPref.height : Integer.MAX_VALUE));
        }
        comp.setAlignmentX(0f);

        // Allow the row itself to expand vertically when its child can
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        row.add(jLabel);
        row.add(Box.createHorizontalStrut(8));
        row.add(comp);
        return row;
    }
}
