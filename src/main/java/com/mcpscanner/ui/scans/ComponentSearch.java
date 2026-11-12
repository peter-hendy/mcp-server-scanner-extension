package com.mcpscanner.ui.scans;

import javax.swing.JTabbedPane;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;

final class ComponentSearch {

    private ComponentSearch() {
    }

    static <T extends Component> Optional<T> first(Component root, Class<T> type, Predicate<T> matches) {
        Deque<Component> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            Component component = queue.poll();
            if (type.isInstance(component) && matches.test(type.cast(component))) {
                return Optional.of(type.cast(component));
            }
            if (component instanceof Container container) {
                queue.addAll(Arrays.asList(container.getComponents()));
            }
        }
        return Optional.empty();
    }

    static <T extends Component> Optional<T> named(Component root, Class<T> type, String name) {
        return first(root, type, component -> Objects.equals(name, component.getName())
                && visibleWithin(root, component));
    }

    private static boolean visibleWithin(Component root, Component component) {
        for (Component current = component; current != null && current != root; current = current.getParent()) {
            if (!current.isVisible()) {
                return false;
            }
        }
        return true;
    }

    static Stream<String> titles(JTabbedPane tabs) {
        return IntStream.range(0, tabs.getTabCount()).mapToObj(tabs::getTitleAt);
    }

    static Optional<Component> selectTab(JTabbedPane tabs, Predicate<String> title) {
        return IntStream.range(0, tabs.getTabCount())
                .filter(index -> title.test(tabs.getTitleAt(index)))
                .boxed()
                .findFirst()
                .map(index -> {
                    tabs.setSelectedIndex(index);
                    return tabs.getComponentAt(index);
                });
    }
}
