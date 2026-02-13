package com.github.fligneul.debtplugin.debt.toolwindow.chart;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Very lightweight pie chart panel used in the Debt toolwindow to visualize
 * the distribution of debt items by current module.
 */
public class ModulePieChartPanel extends JPanel {
    private final LinkedHashMap<String, Integer> data = new LinkedHashMap<>();

    public ModulePieChartPanel() {
        setOpaque(true);
        setBackground(Color.WHITE);
    }

    public void setData(Map<String, Integer> newData) {
        data.clear();
        if (newData != null) {
            // Keep insertion order for stable coloring/legend
            if (!(newData instanceof LinkedHashMap)) {
                data.putAll(new LinkedHashMap<>(newData));
            } else {
                data.putAll(newData);
            }
        }
        revalidate();
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(480, 360);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int total = data.values().stream().mapToInt(Integer::intValue).sum();
            if (total <= 0) {
                drawCenteredText(g2, "No data", getWidth(), getHeight());
                return;
            }

            // Layout: pie on the left, legend on the right
            int padding = 16;
            int legendWidth = Math.max(180, (int) (getWidth() * 0.33));
            int pieSize = Math.min(getHeight() - padding * 2, getWidth() - legendWidth - padding * 3);
            int cx = padding + pieSize / 2;
            int cy = getHeight() / 2;
            int pieX = cx - pieSize / 2;
            int pieY = cy - pieSize / 2;

            double start = 0.0;
            List<LegendItem> legendItems = new ArrayList<>();
            int index = 0;
            for (Map.Entry<String, Integer> e : data.entrySet()) {
                String label = Objects.toString(e.getKey(), "Unknown");
                int value = Math.max(0, e.getValue());
                if (value == 0) continue;
                Color color = colorForIndex(index++);
                double extent = 360.0 * value / total;

                g2.setColor(color);
                Arc2D.Double arc = new Arc2D.Double(pieX, pieY, pieSize, pieSize, start, extent, Arc2D.PIE);
                g2.fill(arc);

                legendItems.add(new LegendItem(color, label, value));
                start += extent;
            }

            // Draw a thin outline
            g2.setColor(new Color(0, 0, 0, 60));
            g2.setStroke(new BasicStroke(1f));
            g2.drawOval(pieX, pieY, pieSize, pieSize);

            // Legend
            int legendX = pieX + pieSize + padding * 2;
            int legendY = padding + 4;
            FontMetrics fm = g2.getFontMetrics();
            for (LegendItem it : legendItems) {
                // Color box
                int box = 12;
                g2.setColor(it.color);
                g2.fillRect(legendX, legendY, box, box);
                g2.setColor(new Color(0, 0, 0, 100));
                g2.drawRect(legendX, legendY, box, box);

                // Text
                String text = it.label + " (" + it.value + ")";
                g2.setColor(Color.DARK_GRAY);
                g2.drawString(text, legendX + box + 8, legendY + box - 2);

                legendY += Math.max(20, fm.getHeight());
            }

            // Title
            g2.setColor(Color.DARK_GRAY);
            Font old = g2.getFont();
            g2.setFont(old.deriveFont(Font.BOLD, Math.max(12f, old.getSize2D() + 2f)));
            String title = "Debts by Module";
            int tw = g2.getFontMetrics().stringWidth(title);
            g2.drawString(title, Math.max(padding, cx - tw / 2), Math.max(padding + 2, pieY - 8));
            g2.setFont(old);
        } finally {
            g2.dispose();
        }
    }

    private void drawCenteredText(Graphics2D g2, String text, int w, int h) {
        FontMetrics fm = g2.getFontMetrics();
        int x = (w - fm.stringWidth(text)) / 2;
        int y = (h - fm.getHeight()) / 2 + fm.getAscent();
        g2.setColor(Color.GRAY);
        g2.drawString(text, x, y);
    }

    private Color colorForIndex(int i) {
        // Fixed pleasant palette with fallback hashing
        Color[] palette = new Color[] {
                new Color(0x4E79A7), // blue
                new Color(0xF28E2B), // orange
                new Color(0xE15759), // red
                new Color(0x76B7B2), // teal
                new Color(0x59A14F), // green
                new Color(0xEDC949), // yellow
                new Color(0xAF7AA1), // purple
                new Color(0xFF9DA7), // pink
                new Color(0x9C755F), // brown
                new Color(0xBAB0AC)  // gray
        };
        if (i < palette.length) return palette[i];
        // Hash-based fallback for stability
        int h = Integer.hashCode(i);
        int r = 100 + Math.abs(h * 37) % 156;
        int g = 100 + Math.abs(h * 57) % 156;
        int b = 100 + Math.abs(h * 97) % 156;
        return new Color(r, g, b);
    }

    private static class LegendItem {
        final Color color;
        final String label;
        final int value;
        LegendItem(Color color, String label, int value) {
            this.color = color;
            this.label = label;
            this.value = value;
        }
    }
}
