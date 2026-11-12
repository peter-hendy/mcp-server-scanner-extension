package com.mcpscanner.ui.detail;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.text.JTextComponent;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

final class Components {

    private Components() {
    }

    static <T extends Component> List<T> within(Container root, Class<T> type) {
        List<T> found = new ArrayList<>();
        collect(root, type, found);
        return found;
    }

    static List<String> shownTexts(Container root) {
        List<String> texts = within(root, JLabel.class).stream().map(JLabel::getText).collect(Collectors.toList());
        within(root, JTextComponent.class).forEach(text -> texts.add(text.getText()));
        return texts;
    }

    static boolean htmlDisabled(JComponent component) {
        return Boolean.TRUE.equals(component.getClientProperty("html.disable"));
    }

    private static <T extends Component> void collect(Component component, Class<T> type, List<T> found) {
        if (type.isInstance(component)) {
            found.add(type.cast(component));
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                collect(child, type, found);
            }
        }
    }
}
