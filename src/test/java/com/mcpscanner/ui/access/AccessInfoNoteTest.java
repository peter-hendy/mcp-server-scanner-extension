package com.mcpscanner.ui.access;

import com.mcpscanner.access.AccessCell;
import com.mcpscanner.access.AccessItem;
import com.mcpscanner.access.AccessRow;
import com.mcpscanner.access.AccessSnapshot;
import com.mcpscanner.access.ListingState;
import com.mcpscanner.checks.api.AccessExpectation;
import com.mcpscanner.checks.api.AccessOutcome;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.mcpscanner.access.AccessFixtures.ALICE;
import static com.mcpscanner.access.AccessFixtures.tool;
import static org.assertj.core.api.Assertions.assertThat;

class AccessInfoNoteTest {

    @Test
    void disconnectedAsksForAConnection() {
        assertThat(AccessInfoNote.of(new AccessSnapshot(false, List.of(), List.of())))
                .contains(AccessInfoNote.DISCONNECTED);
    }

    @Test
    void nothingReplayedExplainsTheListingIsFromConnect() {
        assertThat(AccessInfoNote.of(snapshot(row("a", false), row("b", false))))
                .contains(AccessInfoNote.LISTING_ONLY);
    }

    @Test
    void aPartlyReplayedMatrixSaysTheRestArePendingNotRefused() {
        assertThat(AccessInfoNote.of(snapshot(row("a", true), row("b", false), row("c", false))))
                .hasValueSatisfying(note -> assertThat(note)
                        .contains("1 of 3 items replayed so far")
                        .contains("not yet checked are pending, not refused: run Check access"));
    }

    @Test
    void aFullyReplayedMatrixNeedsNoNote() {
        assertThat(AccessInfoNote.of(snapshot(row("a", true)))).isEmpty();
    }

    private static AccessSnapshot snapshot(AccessRow... rows) {
        return new AccessSnapshot(true, List.of(ALICE), List.of(rows));
    }

    private static AccessRow row(String name, boolean replayed) {
        Optional<AccessOutcome> outcome = replayed ? Optional.of(AccessOutcome.REFUSED) : Optional.empty();
        return new AccessRow(AccessItem.tool(tool(name)), ALICE, AccessExpectation.ANY_SIGNED_IN, true, Map.of(),
                AccessCell.of(ListingState.NO_LISTING, outcome, false));
    }
}
