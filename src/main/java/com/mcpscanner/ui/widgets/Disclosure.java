package com.mcpscanner.ui.widgets;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BorderLayout;

public final class Disclosure extends JPanel {

    private static final String COLLAPSED = "▸ ";
    private static final String EXPANDED = "▾ ";

    private final JButton toggle = new JButton();
    private final JComponent content;
    private String title;

    public Disclosure(String title, JComponent content) {
        super(new BorderLayout());
        this.title = title;
        this.content = content;
        setOpaque(false);
        SwingHtmlGuard.disableHtml(toggle);
        toggle.putClientProperty("JButton.buttonType", "borderless");
        toggle.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
        toggle.setContentAreaFilled(false);
        toggle.setHorizontalAlignment(JButton.LEADING);
        toggle.addActionListener(event -> setExpanded(!isExpanded()));
        add(toggle, BorderLayout.NORTH);
        add(content, BorderLayout.CENTER);
        setExpanded(false);
    }

    public void setTitle(String title) {
        this.title = title;
        renderTitle();
    }

    public void expand() {
        setExpanded(true);
    }

    public boolean isExpanded() {
        return content.isVisible();
    }

    public JButton toggle() {
        return toggle;
    }

    private void setExpanded(boolean expanded) {
        content.setVisible(expanded);
        renderTitle();
        revalidate();
        repaint();
    }

    private void renderTitle() {
        toggle.setText((isExpanded() ? EXPANDED : COLLAPSED) + title);
    }
}
