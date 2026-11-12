package com.mcpscanner.ui.scans;

import com.mcpscanner.testutil.SwingTree;

import java.awt.Component;
import java.awt.Container;
import java.util.List;

final class Find {

    private Find() {
    }

    static <T extends Component> T named(Container root, Class<T> type, String name) {
        return all(root, type).stream().filter(component -> name.equals(component.getName())).findFirst()
                .orElseThrow(() -> new AssertionError("no " + name));
    }

    static <T extends Component> List<T> all(Container root, Class<T> type) {
        return SwingTree.descendants(root).stream().filter(type::isInstance).map(type::cast).toList();
    }
}
