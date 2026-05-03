package com.github.fligneul.debtplugin.debt.toolwindow.chart.panel;

import com.github.fligneul.debtplugin.debt.model.DebtItem;
import com.github.fligneul.debtplugin.debt.toolwindow.chart.EClassifiers;

import javax.swing.JPanel;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

public abstract class AChartPanel extends JPanel implements IChartsPanel {

    protected final List<ChartModel> data = new ArrayList<>();
    protected String title;
    protected EClassifiers groupBy;

    public AChartPanel() {
        setOpaque(true);
        setBackground(Color.WHITE);
        setGroupBy(EClassifiers.DEFAULT);
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
