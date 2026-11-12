package com.mcpscanner.ui.widgets;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JToggleButton;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class SideDrawer extends JPanel {

    private static final int PANEL_WIDTH = 460;
    private static final double MAX_PANEL_SHARE = 0.45;
    private static final int MIN_PANEL_WIDTH = 280;

    private final JComponent main;
    private final CardLayout sectionCards = new CardLayout();
    private final JPanel sectionArea = new JPanel(sectionCards);
    private final JPanel strip = new JPanel();
    private final Map<String, SideTab> tabs = new LinkedHashMap<>();
    private final JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
    private String expanded;
    private boolean dividerPlaced;

    public SideDrawer(JComponent main) {
        super(new BorderLayout());
        this.main = main;
        configureSplit();
        configureStrip();
        add(main, BorderLayout.CENTER);
        add(strip, BorderLayout.EAST);
    }

    public void addSection(String title, char mnemonic, JComponent content) {
        SideTab tab = new SideTab(title);
        tab.setMnemonic(mnemonic);
        tab.setAlignmentX(Component.CENTER_ALIGNMENT);
        tab.addActionListener(event -> onToggle(title));
        tabs.put(title, tab);
        strip.add(tab);
        sectionArea.add(content, title);
    }

    public void expand(String title) {
        expanded = title;
        sectionCards.show(sectionArea, title);
        tabs.forEach((name, tab) -> tab.setSelected(name.equals(title)));
        tabs.get(title).showPlain();
        showSplit();
    }

    public void collapse() {
        expanded = null;
        tabs.values().forEach(tab -> tab.setSelected(false));
        remove(split);
        add(main, BorderLayout.CENTER);
        refreshLayout();
    }

    public Optional<String> expandedSection() {
        return Optional.ofNullable(expanded);
    }

    public void alert(String title, String reason) {
        if (title.equals(expanded)) {
            return;
        }
        tabs.get(title).showAlert(reason);
    }

    public JToggleButton toggleFor(String title) {
        return tabs.get(title);
    }

    public void relabel(String title, String label) {
        tabs.get(title).relabel(label);
        strip.revalidate();
        strip.repaint();
    }

    public List<String> sectionOrder() {
        return List.copyOf(tabs.keySet());
    }

    public String labelOf(String title) {
        return tabs.get(title).text();
    }

    private void onToggle(String title) {
        if (title.equals(expanded)) {
            collapse();
        } else {
            expand(title);
        }
    }

    private void showSplit() {
        if (split.getParent() == this) {
            return;
        }
        remove(main);
        split.setLeftComponent(main);
        split.setRightComponent(sectionArea);
        add(split, BorderLayout.CENTER);
        placeDivider();
        refreshLayout();
    }

    private void configureStrip() {
        strip.setLayout(new BoxLayout(strip, BoxLayout.Y_AXIS));
        strip.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, ThemeColors.separator()));
    }

    private void configureSplit() {
        split.setResizeWeight(1.0);
        split.setContinuousLayout(true);
        split.setBorder(null);
        main.setMinimumSize(new Dimension(0, 0));
        sectionArea.setMinimumSize(new Dimension(MIN_PANEL_WIDTH, 0));
        sectionArea.setPreferredSize(new Dimension(PANEL_WIDTH, 0));
        split.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                if (!dividerPlaced) {
                    placeDivider();
                }
            }
        });
    }

    private void placeDivider() {
        int width = split.getWidth() > 0 ? split.getWidth() : getWidth() - strip.getWidth();
        if (width <= 0) {
            return;
        }
        int panelWidth = Math.max(MIN_PANEL_WIDTH, Math.min(PANEL_WIDTH, (int) (width * MAX_PANEL_SHARE)));
        split.setDividerLocation(Math.max(0, width - panelWidth - split.getDividerSize()));
        dividerPlaced = true;
    }

    private void refreshLayout() {
        revalidate();
        repaint();
    }
}
