package com.mcpscanner.engine;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public record ScanSnapshot(long id, ScanLabel label, ScanStatus status, Instant startedAt,
                           Optional<Instant> endedAt, ScanProgress progress, int requests, ScanErrors errors,
                           List<RunningInvocation> nowRunning, ScanFindings issues) {

    public boolean ended() {
        return endedAt.isPresent();
    }

    public boolean awaitingCollaborator() {
        return ended() && progress.watchedPayloads() > 0;
    }
}
