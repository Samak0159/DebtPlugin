package com.github.fligneul.debtplugin.debt.toolwindow;

import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.FlowLayout;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * A small UI component: a button that opens a popup menu with checkboxes for multi-select filtering.
 * - Call setOptions(...) to populate available choices.
 * - Users can toggle multiple values. Empty selection means "All".
 * - Register listeners via addSelectionListener(Runnable) to be notified on changes.
 */
public class MultiSelectFilter<T> extends JPanel {
    private final JButton button;
    private final JPopupMenu popup = new JPopupMenu();
    private final List<Runnable> listeners = new ArrayList<>();
    private final Set<T> options = new LinkedHashSet<>();
    private final Set<T> selected = new LinkedHashSet<>();
    private Function<T, String> renderer = String::valueOf;

    public MultiSelectFilter(String label) {
        super(new FlowLayout(FlowLayout.LEFT, 0, 0));
        this.button = new JButton(label + ": All");
        this.add(button);
        button.addActionListener(e -> {
            if (!popup.isVisible()) {
                popup.show(button, 0, button.getHeight());
            } else {
                popup.setVisible(false);
            }
        });
    }

    public void setRenderer(Function<T, String> renderer) {
        this.renderer = renderer != null ? renderer : String::valueOf;
        rebuildPopup();
        updateButtonText();
    }

    public void setOptions(Collection<T> newOptions) {
        options.clear();
        if (newOptions != null) options.addAll(newOptions);
        // Keep only still-existing selections
        selected.retainAll(options);
        rebuildPopup();
        updateButtonText();
    }

    public Set<T> getSelected() {
        return new LinkedHashSet<>(selected);
    }

    public void setSelected(Collection<T> values) {
        selected.clear();
        if (values != null) {
            for (T v : values) {
                if (options.contains(v)) selected.add(v);
            }
        }
        updateChecksFromState();
        updateButtonText();
        notifyListeners();
    }

    public void clearSelection() {
        selected.clear();
        updateChecksFromState();
        updateButtonText();
        notifyListeners();
    }

    public void addSelectionListener(Runnable listener) {
        if (listener != null) listeners.add(listener);
    }

    private void rebuildPopup() {
        popup.removeAll();
        for (T opt : options) {
            String text = renderer.apply(opt);
            JCheckBoxMenuItem item = new JCheckBoxMenuItem(text, selected.contains(opt));
            item.addActionListener(e -> {
                if (item.isSelected()) selected.add(opt); else selected.remove(opt);
                updateButtonText();
                notifyListeners();
            });
            popup.add(item);
        }
    }

    private void updateChecksFromState() {
        // Sync checkbox states with current selection
        int i = 0;
        for (var comp : popup.getComponents()) {
            if (comp instanceof JCheckBoxMenuItem item) {
                // options iteration order matches popup items
                T opt = options.stream().skip(i).findFirst().orElse(null);
                if (opt != null) item.setSelected(selected.contains(opt));
                i++;
            }
        }
    }

    private void updateButtonText() {
        String text;
        if (selected.isEmpty()) text = "All";
        else if (selected.size() <= 3) text = String.join(", ", selected.stream().map(o -> renderer.apply(o)).toList());
        else text = selected.size() + " selected";
        button.setText(button.getText().replaceFirst(":.*$", ": " + text));
    }

    private void notifyListeners() {
        // Dispatch later to avoid popup reentrancy issues
        SwingUtilities.invokeLater(() -> listeners.forEach(Runnable::run));
    }
}
