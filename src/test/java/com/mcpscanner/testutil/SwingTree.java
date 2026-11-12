package com.mcpscanner.testutil;

import javax.swing.JButton;
import javax.swing.JLabel;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class SwingTree {

    private SwingTree() {
    }

    public static List<Component> descendants(Container root) {
        List<Component> found = new ArrayList<>();
        for (Component child : root.getComponents()) {
            found.add(child);
            if (child instanceof Container container) {
                found.addAll(descendants(container));
            }
        }
        return found;
    }

    public static List<Component> visibleDescendants(Container root) {
        List<Component> found = new ArrayList<>();
        for (Component child : root.getComponents()) {
            if (!child.isVisible()) {
                continue;
            }
            found.add(child);
            if (child instanceof Container container) {
                found.addAll(visibleDescendants(container));
            }
        }
        return found;
    }

    public static JButton button(Container root, String text) {
        return descendants(root).stream()
                .filter(JButton.class::isInstance)
                .map(JButton.class::cast)
                .filter(button -> text.equals(button.getText()))
                .findFirst()
                .orElseThrow();
    }

    public static Optional<Component> labelledField(Container root, String labelText) {
        return descendants(root).stream()
                .filter(JLabel.class::isInstance)
                .map(JLabel.class::cast)
                .filter(label -> labelText.equals(label.getText()))
                .map(JLabel::getLabelFor)
                .findFirst();
    }

    public static void layOut(Container root) {
        root.doLayout();
        for (Component child : root.getComponents()) {
            if (child instanceof Container nested) {
                layOut(nested);
            }
        }
    }
}
