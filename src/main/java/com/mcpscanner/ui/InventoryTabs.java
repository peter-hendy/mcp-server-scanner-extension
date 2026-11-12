package com.mcpscanner.ui;

import javax.swing.JTabbedPane;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

final class InventoryTabs extends JTabbedPane {

    private final List<CountedTab> counted = new ArrayList<>();

    void addCounted(String title, Component content, IntSupplier selected, IntSupplier total) {
        counted.add(new CountedTab(getTabCount(), title, selected, total));
        addTab(title, content);
    }

    private final List<TitledTab> titled = new ArrayList<>();

    void addTitled(Component content, Supplier<String> title) {
        titled.add(new TitledTab(getTabCount(), title));
        addTab(title.get(), content);
    }

    void refreshCounts() {
        for (CountedTab tab : counted) {
            setTitleAt(tab.index(), tab.title());
        }
        for (TitledTab tab : titled) {
            setTitleAt(tab.index(), tab.title().get());
        }
    }

    private record TitledTab(int index, Supplier<String> title) {
    }

    private record CountedTab(int index, String name, IntSupplier selected, IntSupplier total) {
        String title() {
            int all = total.getAsInt();
            return all == 0 ? name + " (0)" : name + " (" + selected.getAsInt() + "/" + all + ")";
        }
    }
}
