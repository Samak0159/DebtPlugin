package com.github.fligneul.debtplugin.debt.toolwindow.chart.panel;

import com.github.fligneul.debtplugin.debt.model.DebtItem;
import com.github.fligneul.debtplugin.debt.toolwindow.chart.EClassifiers;

import javax.swing.JPanel;
import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

public abstract class AChartPanel extends JPanel implements IChartsPanel {

    protected final LinkedHashMap<String, Integer> data = new LinkedHashMap<>();
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
    public void setData(List<DebtItem> items) {
        data.clear();
        var newData = extractData(items);
        if (newData != null) {
            if (!(newData instanceof LinkedHashMap)) {
                data.putAll(new LinkedHashMap<>(newData));
            } else {
                data.putAll(newData);
            }
        }
        revalidate();
        repaint();
    }

    private Map<String, Integer> extractData(List<DebtItem> items) {
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
                .collect(Collectors.groupingBy(classifier, TreeMap::new, Collectors.summingInt(e -> 1)));
    }

}
