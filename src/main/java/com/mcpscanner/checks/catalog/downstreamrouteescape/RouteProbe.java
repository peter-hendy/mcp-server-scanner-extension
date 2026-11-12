package com.mcpscanner.checks.catalog.downstreamrouteescape;

import java.util.List;

record RouteProbe(List<String> traversals, List<String> controls, List<String> contrasts) {

    RouteProbe {
        traversals = List.copyOf(traversals);
        controls = List.copyOf(controls);
        contrasts = List.copyOf(contrasts);
    }
}
