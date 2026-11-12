package com.mcpscanner.access;

import java.util.Optional;

record RowOutcome(int issues, Optional<String> transportFailure) {

    static RowOutcome checked(int issues) {
        return new RowOutcome(issues, Optional.empty());
    }

    static RowOutcome unreachable(String reason) {
        return new RowOutcome(0, Optional.of(reason));
    }
}
