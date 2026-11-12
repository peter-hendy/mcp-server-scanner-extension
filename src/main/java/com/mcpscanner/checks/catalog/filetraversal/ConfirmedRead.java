package com.mcpscanner.checks.catalog.filetraversal;

import java.util.List;

record ConfirmedRead(TraversalAttempt candidate, List<TraversalAttempt> controls) {

    ConfirmedRead {
        controls = List.copyOf(controls);
    }

    Disclosure disclosure() {
        return candidate.disclosure();
    }

    TraversalPayload payload() {
        return candidate.payload();
    }
}
