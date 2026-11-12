package com.mcpscanner.checks.catalog.downstreamrouteescape;

import java.util.ArrayList;
import java.util.List;

final class RouteProbeCatalog {

    private static final int MAX_DEPTH = 5;
    private static final String SUFFIX_NEUTRALISER = "?mcp_route=1";
    private static final String DETOUR = "mcp/../";

    private RouteProbeCatalog() {}

    static List<RouteProbe> standard() {
        List<RouteProbe> probes = new ArrayList<>();
        for (int depth = 1; depth <= MAX_DEPTH; depth++) {
            probes.add(dotSlash(depth, ""));
            probes.add(dotSlash(depth, SUFFIX_NEUTRALISER));
        }
        probes.add(new RouteProbe(List.of("..", ".."), List.of("...", "...."), List.of(".")));
        return List.copyOf(probes);
    }

    private static RouteProbe dotSlash(int depth, String suffix) {
        String traversal = "../".repeat(depth);
        String control = ".../".repeat(depth);
        return new RouteProbe(
                List.of(traversal + suffix, traversal + "./" + suffix),
                List.of(control + suffix, control + "./" + suffix, control + DETOUR + suffix),
                List.of());
    }
}
