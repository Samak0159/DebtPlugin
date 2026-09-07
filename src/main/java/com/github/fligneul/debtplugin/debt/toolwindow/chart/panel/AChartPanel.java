package com.github.fligneul.debtplugin.debt.toolwindow.chart.panel;

import com.github.fligneul.debtplugin.debt.model.DebtItem;
import com.github.fligneul.debtplugin.debt.toolwindow.chart.EClassifiers;

import com.github.fligneul.debtplugin.debt.service.DebtService;
import com.intellij.openapi.project.Project;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

public abstract class AChartPanel extends JPanel implements IChartsPanel {

    protected final List<ChartModel> data = new ArrayList<>();
    protected final Project project;
    protected String title;
    protected EClassifiers groupBy;

    public AChartPanel(final EClassifiers chartClassifier, final Project project) {
        this.project = project;
        setOpaque(true);
        setBackground(Color.WHITE);
        setGroupBy(chartClassifier);

        MouseAdapter mouseAdapter = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                String category = getCategoryAt(e.getPoint());
                if (category == null) {
                    setCursor(Cursor.getDefaultCursor());
                    setToolTipText(null);
                } else {
                    setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                    setToolTipText("Filter by " + (groupBy != null ? groupBy.name() : "") + ": " + category);
                }
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    String category = getCategoryAt(e.getPoint());
                    if (category != null) {
                        notifyCategoryClicked(category);
                    }
                }
            }
        };
        addMouseListener(mouseAdapter);
        addMouseMotionListener(mouseAdapter);
    }

    protected abstract String getCategoryAt(Point p);

    protected void notifyCategoryClicked(String categoryName) {
        if (project != null && groupBy != null && categoryName != null) {
            project.getMessageBus().syncPublisher(DebtService.SELECTION_TOPIC).select(groupBy, categoryName);
        }
    }

    @Override
    public void setGroupBy(final EClassifiers groupByField) {
        this.groupBy = groupByField;
        this.title = "Debts by " + groupByField.name();
    }

    @Override
    public void setData(List<DebtItem> items, final int limit) {
        data.clear();

        this.data.addAll(extractData(items)
                .stream()
                .limit(limit)
                .toList());
        revalidate();
        repaint();
    }

    private List<ChartModel> extractData(List<DebtItem> items) {
        final Function<DebtItem, String> classifier = switch (groupBy) {
            case WantedLevel -> item -> String.valueOf(item.getWantedLevel());
            case Complexity -> item -> String.valueOf(item.getComplexity());
            case Status -> item -> String.valueOf(item.getStatus());
            case Estimation -> item -> String.valueOf(item.getEstimation());
            case Risk -> item -> String.valueOf(item.getRisk());
            case Module -> item -> {
                String m = item.getCurrentModule();
                return (m == null || m.isBlank()) ? "Unknown" : m;
            };
            case Priority -> debtItem -> debtItem.getPriority().isEmpty()
                    ? "Unknown"
                    : debtItem.getPriority();
            case Type -> debtItem -> debtItem.getType().isEmpty()
                    ? "Unknown"
                    : debtItem.getType();
        };

        return items.stream()
                .collect(Collectors.groupingBy(classifier, TreeMap::new, Collectors.summingInt(e -> 1)))
                .entrySet()
                .stream()
                .map(entry -> new ChartModel(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(ChartModel::nbValues).reversed())
                .toList();
    }

    protected record ChartModel(String name, int nbValues) {
    }

}
