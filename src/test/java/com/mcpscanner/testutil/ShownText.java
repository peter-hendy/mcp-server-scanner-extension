package com.mcpscanner.testutil;

import com.mcpscanner.ui.detail.FieldGrid;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.text.JTextComponent;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public final class ShownText {

    private ShownText() {
    }

    public static String of(Component component) {
        if (!component.isVisible()) {
            return "";
        }
        if (component instanceof FieldGrid grid) {
            return rows(grid);
        }
        if (component instanceof JLabel label) {
            return Objects.toString(label.getText(), "");
        }
        if (component instanceof JTextComponent text) {
            return text.getText();
        }
        if (component instanceof Container container) {
            return joined(Arrays.asList(container.getComponents()));
        }
        return "";
    }

    public static boolean everyLabelIsHtmlDisabled(Container root) {
        return SwingTree.descendants(root).stream()
                .filter(JLabel.class::isInstance).map(JComponent.class::cast)
                .allMatch(label -> Boolean.TRUE.equals(label.getClientProperty("html.disable")));
    }

    private static String rows(FieldGrid grid) {
        Component[] cells = grid.getComponents();
        List<String> rows = new ArrayList<>();
        for (int index = 0; index + 1 < cells.length; index += 2) {
            rows.add(of(cells[index]) + ": " + of(cells[index + 1]));
        }
        return String.join("\n", rows);
    }

    private static String joined(List<Component> children) {
        return children.stream().map(ShownText::of).filter(text -> !text.isEmpty())
                .collect(Collectors.joining("\n"));
    }
}
