package com.mcpscanner.ui.access;

import com.mcpscanner.access.AccessRow;
import com.mcpscanner.access.AccessSnapshot;

import java.util.Optional;

final class AccessInfoNote {

    static final String NOT_YET_CHECKED = "not yet checked";
    static final String LISTING_ONLY = "ⓘ  Listing from Connect: each identity ran its own tools/list and "
            + "resources/list. Calls " + NOT_YET_CHECKED + ": run Check access to replay every item.";
    static final String DISCONNECTED = "ⓘ  Connect to list what each identity can see.";

    private AccessInfoNote() {
    }

    static Optional<String> of(AccessSnapshot snapshot) {
        if (!snapshot.connected()) {
            return Optional.of(DISCONNECTED);
        }
        long checked = snapshot.rows().stream().filter(AccessRow::checked).count();
        if (checked == 0) {
            return Optional.of(LISTING_ONLY);
        }
        return checked < snapshot.rows().size() ? Optional.of(partlyChecked(checked, snapshot.rows().size()))
                : Optional.empty();
    }

    private static String partlyChecked(long checked, int total) {
        return "ⓘ  " + checked + " of " + total + " items replayed so far (by Scan or an earlier Check access). "
                + "Cells marked " + NOT_YET_CHECKED + " are pending, not refused: run Check access to replay every item.";
    }
}
