package com.github.fligneul.debtplugin.debt.toolwindow.relationship;

import com.github.fligneul.debtplugin.debt.model.DebtItem;
import com.github.fligneul.debtplugin.debt.model.Relationship;
import com.github.fligneul.debtplugin.debt.service.DebtProviderService;
import com.intellij.openapi.project.Project;
import com.intellij.ui.JBColor;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RelationshipGraphPanel extends JPanel {
    private static final int NODE_WIDTH = 120;
    private static final int NODE_HEIGHT = 40;
    private static final int PADDING = 20;

    private final DebtProviderService debtProviderService;
    private final List<DebtItem> items = new ArrayList<>();
    private final Map<String, Point> nodePositions = new HashMap<>();

    public RelationshipGraphPanel(final Project project) {
        debtProviderService = project.getService(DebtProviderService.class);
        setBackground(JBColor.background());
    }

    public void update() {
        // Only keep items that have at least one link
        final List<DebtItem> debtsWithLinks = debtProviderService.currentItems()
                .stream()
                .filter(debtItem -> !debtItem.getLinks().isEmpty())
                .toList();

        final List<DebtItem> linked = debtProviderService.currentItems()
                .stream()
                .filter(debtItem -> debtsWithLinks.stream()
                        .map(DebtItem::getLinks)
                        .anyMatch(links -> links.containsKey(debtItem.getId())))
                .toList();

        items.clear();
        items.addAll(debtsWithLinks);
        items.addAll(linked);

        calculateLayout();
        repaint();
    }

    private void calculateLayout() {
        nodePositions.clear();
        if (items.isEmpty()) return;

        int n = items.size();
        int radius = Math.max(200, n * 30);
        int centerX = getWidth() / 2;
        int centerY = getHeight() / 2;

        if (centerX == 0) centerX = radius + PADDING + NODE_WIDTH / 2;
        if (centerY == 0) centerY = radius + PADDING + NODE_HEIGHT / 2;

        for (int i = 0; i < n; i++) {
            double angle = 2 * Math.PI * i / n;
            int x = (int) (centerX + radius * Math.cos(angle)) - NODE_WIDTH / 2;
            int y = (int) (centerY + radius * Math.sin(angle)) - NODE_HEIGHT / 2;
            nodePositions.put(items.get(i).getId(), new Point(x, y));
        }

        // Adjust preferred size based on layout
        int maxX = 0, maxY = 0;
        for (Point p : nodePositions.values()) {
            maxX = Math.max(maxX, p.x + NODE_WIDTH + PADDING);
            maxY = Math.max(maxY, p.y + NODE_HEIGHT + PADDING);
        }
        setPreferredSize(new Dimension(maxX, maxY));
        revalidate();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (items.isEmpty()) {
            g.setColor(JBColor.GRAY);
            g.drawString("No debts with links to display", 20, 20);
            return;
        }

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Draw Links
        for (DebtItem item : items) {
            Point start = nodePositions.get(item.getId());
            if (start == null) continue;

            Map<String, Relationship> links = item.getLinks();
            for (Map.Entry<String, Relationship> entry : links.entrySet()) {
                String targetId = entry.getKey();
                Relationship rel = entry.getValue();
                Point end = nodePositions.get(targetId);
                if (end == null) continue;

                drawLink(g2, start, end, rel);
            }
        }

        // Draw Nodes
        for (DebtItem item : items) {
            Point p = nodePositions.get(item.getId());
            if (p == null) continue;
            drawNode(g2, p, item.getTitle());
        }

        g2.dispose();
    }

    private void drawNode(Graphics2D g2, Point p, String title) {
        g2.setColor(JBColor.namedColor("Panel.background", new JBColor(0xeeeeee, 0x3c3f41)));
        g2.fillRoundRect(p.x, p.y, NODE_WIDTH, NODE_HEIGHT, 10, 10);
        g2.setColor(JBColor.namedColor("Component.borderColor", JBColor.GRAY));
        g2.drawRoundRect(p.x, p.y, NODE_WIDTH, NODE_HEIGHT, 10, 10);

        g2.setColor(JBColor.foreground());
        FontMetrics fm = g2.getFontMetrics();
        String truncatedTitle = title;
        if (fm.stringWidth(title) > NODE_WIDTH - 10) {
            truncatedTitle = title.substring(0, Math.min(title.length(), 15)) + "...";
        }
        int tx = p.x + (NODE_WIDTH - fm.stringWidth(truncatedTitle)) / 2;
        int ty = p.y + (NODE_HEIGHT - fm.getHeight()) / 2 + fm.getAscent();
        g2.drawString(truncatedTitle, tx, ty);
    }

    private void drawLink(Graphics2D g2, Point start, Point end, Relationship rel) {
        int x1 = start.x + NODE_WIDTH / 2;
        int y1 = start.y + NODE_HEIGHT / 2;
        int x2 = end.x + NODE_WIDTH / 2;
        int y2 = end.y + NODE_HEIGHT / 2;

        if (rel == Relationship.Duplicated) {
            g2.setColor(JBColor.RED);
            g2.setStroke(new BasicStroke(2f));
        } else {
            g2.setColor(JBColor.GRAY);
            g2.setStroke(new BasicStroke(1f));
        }

        if (rel == Relationship.After) {
            // Draw curve down
            drawCurvedLine(g2, x1, y1, x2, y2, true);
        } else if (rel == Relationship.Before) {
            // Draw curve up
            drawCurvedLine(g2, x1, y1, x2, y2, false);
        } else {
            g2.drawLine(x1, y1, x2, y2);
        }
    }

    private void drawCurvedLine(Graphics2D g2, int x1, int y1, int x2, int y2, boolean down) {
        int ctrlX = (x1 + x2) / 2;
        int ctrlY = (y1 + y2) / 2 + (down ? 50 : -50);

        // If it's more horizontal than vertical, adjust control point to be really "up" or "down"
        if (Math.abs(x1 - x2) > Math.abs(y1 - y2)) {
            ctrlY = (y1 + y2) / 2 + (down ? 50 : -50);
        } else {
            // If more vertical, "up" or "down" might mean left or right relative to the line,
            // but the requirement says "up" and "down".
            ctrlY = (y1 + y2) / 2 + (down ? 50 : -50);
        }

        java.awt.geom.QuadCurve2D q = new java.awt.geom.QuadCurve2D.Float();
        q.setCurve(x1, y1, ctrlX, ctrlY, x2, y2);
        g2.draw(q);

        // Draw arrow head at the end
        drawArrowHead(g2, ctrlX, ctrlY, x2, y2);
    }

    private void drawArrowHead(Graphics2D g2, int x1, int y1, int x2, int y2) {
        double angle = Math.atan2(y2 - y1, x2 - x1);
        int arrowSize = 8;
        int dx1 = (int) (x2 - arrowSize * Math.cos(angle - Math.PI / 6));
        int dy1 = (int) (y2 - arrowSize * Math.sin(angle - Math.PI / 6));
        int dx2 = (int) (x2 - arrowSize * Math.cos(angle + Math.PI / 6));
        int dy2 = (int) (y2 - arrowSize * Math.sin(angle + Math.PI / 6));
        g2.drawLine(x2, y2, dx1, dy1);
        g2.drawLine(x2, y2, dx2, dy2);
    }

    @Override
    public void doLayout() {
        super.doLayout();
        calculateLayout();
    }
}
