package com.github.fligneul.debtplugin.debt.toolwindow.chart.panel;

import com.github.fligneul.debtplugin.debt.model.DebtItem;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

public class BarChartPanel extends AChartPanel {
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

            int totalCount = data.values().stream().mapToInt(Integer::intValue).sum();
            if (totalCount <= 0) {
                drawCenteredText(g2, "No data", getWidth(), getHeight());
                return;
            }

            int maxValue = data.values().stream().mapToInt(Integer::intValue).max().orElse(1);

            int padding = 40;
            int bottomPadding = 60;
            int chartWidth = getWidth() - padding * 2;
            int chartHeight = getHeight() - padding - bottomPadding;

            if (chartWidth <= 0 || chartHeight <= 0) return;

            // Draw axes
            g2.setColor(Color.LIGHT_GRAY);
            g2.drawLine(padding, padding, padding, padding + chartHeight); // Y axis
            g2.drawLine(padding, padding + chartHeight, padding + chartWidth, padding + chartHeight); // X axis

            int barCount = data.size();
            int barWidth = (chartWidth / barCount) * 2 / 3;
            int barGap = (chartWidth / barCount) / 3;
            
            if (barWidth < 5) barWidth = 5;

            int x = padding + barGap;
            int index = 0;
            FontMetrics fm = g2.getFontMetrics();

            for (Map.Entry<String, Integer> entry : data.entrySet()) {
                int value = entry.getValue();
                int barHeight = (int) ((double) value / maxValue * chartHeight);
                
                Color color = colorForIndex(index++);
                g2.setColor(color);
                g2.fillRect(x, padding + chartHeight - barHeight, barWidth, barHeight);
                
                g2.setColor(new Color(0, 0, 0, 60));
                g2.drawRect(x, padding + chartHeight - barHeight, barWidth, barHeight);

                // Label
                g2.setColor(Color.DARK_GRAY);
                String label = Objects.toString(entry.getKey(), "Unknown");
                
                // Truncate label if too long
                if (fm.stringWidth(label) > barWidth + barGap) {
                     // Very simple truncation
                     while (label.length() > 3 && fm.stringWidth(label + "...") > barWidth + barGap * 2) {
                         label = label.substring(0, label.length() - 1);
                     }
                     label += "...";
                }
                
                int labelX = x + (barWidth - fm.stringWidth(label)) / 2;
                int labelY = padding + chartHeight + fm.getAscent() + 5;
                g2.drawString(label, labelX, labelY);
                
                // Value on top of bar
                String valueStr = String.valueOf(value);
                int valueX = x + (barWidth - fm.stringWidth(valueStr)) / 2;
                int valueY = padding + chartHeight - barHeight - 5;
                g2.drawString(valueStr, valueX, valueY);

                x += barWidth + barGap;
            }

            // Title
            g2.setColor(Color.DARK_GRAY);
            Font old = g2.getFont();
            g2.setFont(old.deriveFont(Font.BOLD, Math.max(12f, old.getSize2D() + 2f)));
            int tw = g2.getFontMetrics().stringWidth(this.title);
            g2.drawString(this.title, (getWidth() - tw) / 2, padding / 2 + 5);
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
        Color[] palette = new Color[]{
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
        int h = Integer.hashCode(i);
        int r = 100 + Math.abs(h * 37) % 156;
        int g = 100 + Math.abs(h * 57) % 156;
        int b = 100 + Math.abs(h * 97) % 156;
        return new Color(r, g, b);
    }
}
