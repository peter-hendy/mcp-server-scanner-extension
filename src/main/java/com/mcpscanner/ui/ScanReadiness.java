package com.mcpscanner.ui;

import com.mcpscanner.scan.ScanInventory;
import com.mcpscanner.scan.ScanMode;
import com.mcpscanner.ui.widgets.Plurals;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

record ScanReadiness(boolean connected, ScanInventory selection, Optional<Integer> requestCount) {

    private static final String CONNECT_FIRST = "Connect to an MCP server first";
    private static final String NOTHING_TO_SEND =
            "Tick a tool, resource, template or prompt, or turn on a request family under Requests";
    static final String COUNTING = "Counting requests…";

    ScanReadiness(boolean connected, ScanInventory selection, int requestCount) {
        this(connected, selection, Optional.of(requestCount));
    }

    Optional<String> blocker() {
        if (!connected) {
            return Optional.of(CONNECT_FIRST);
        }
        if (counting()) {
            return Optional.of(COUNTING);
        }
        return sendsNothing() ? Optional.of(NOTHING_TO_SEND) : Optional.empty();
    }

    Optional<String> shortBlocker() {
        if (!connected) {
            return Optional.of("Connect, then tick items to scan");
        }
        return sendsNothing() ? Optional.of("Tick items to scan") : Optional.empty();
    }

    String buttonLabel() {
        if (sendsNothing()) {
            return "Scan";
        }
        if (itemsTicked()) {
            return "Scan " + Plurals.count(selection.totalCount(), "item");
        }
        return counting() ? "Scan" : "Scan " + requests();
    }

    String summary() {
        return itemsTicked() ? describe(selection) : requests();
    }

    String tooltip(ScanMode mode) {
        return blocker().orElseGet(() -> launchDescription() + " with " + ScanModeText.of(mode).label());
    }

    private String launchDescription() {
        return itemsTicked() ? "Scan the " + describe(selection).replaceFirst(" item", " selected item")
                : "Send " + requests() + " (no items ticked)";
    }

    private String requests() {
        return Plurals.count(requestCount.orElse(0), "request");
    }

    private boolean itemsTicked() {
        return selection.totalCount() > 0;
    }

    private boolean sendsNothing() {
        return requestCount.filter(count -> count == 0).isPresent();
    }

    private boolean counting() {
        return requestCount.isEmpty();
    }

    static String describe(ScanInventory inventory) {
        List<String> kinds = new ArrayList<>();
        addKind(kinds, inventory.tools().size(), "tool");
        addKind(kinds, inventory.resources().size(), "resource");
        addKind(kinds, inventory.resourceTemplates().size(), "template");
        addKind(kinds, inventory.prompts().size(), "prompt");
        return Plurals.count(inventory.totalCount(), "item") + " (" + String.join(", ", kinds) + ")";
    }

    private static void addKind(List<String> kinds, int count, String noun) {
        if (count > 0) {
            kinds.add(Plurals.count(count, noun));
        }
    }
}
