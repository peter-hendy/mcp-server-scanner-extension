package com.mcpscanner.ui.access;

import com.mcpscanner.access.AccessRow;
import com.mcpscanner.access.AccessSnapshot;

import java.util.List;
import java.util.Optional;

final class CheckAccessReadiness {

    static final String NO_COMPARISON = NoComparisonHint.sentence();

    private CheckAccessReadiness() {
    }

    static Optional<String> blocker(AccessSnapshot snapshot, List<AccessRow> rows) {
        if (!snapshot.connected()) {
            return Optional.of("Connect to an MCP server first");
        }
        if (snapshot.comparedIdentities().isEmpty()) {
            return Optional.of(NO_COMPARISON);
        }
        if (rows.isEmpty()) {
            return Optional.of("No tools or resources to compare");
        }
        return Optional.empty();
    }
}
