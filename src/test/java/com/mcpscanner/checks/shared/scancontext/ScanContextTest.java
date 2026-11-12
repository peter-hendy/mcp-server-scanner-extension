package com.mcpscanner.checks.shared.scancontext;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class ScanContextTest {

    @Test
    void aScanWithoutACancellationSignalIsNeverCancelled() {
        assertThat(new ScanContext(1).cancelled()).isFalse();
    }

    @Test
    void followsItsScansCancellationSignalForEveryTestedRequest() {
        AtomicBoolean cancelled = new AtomicBoolean();
        ScanContext scan = new ScanContext(1, cancelled::get).forRequest("tools/call note");

        assertThat(scan.cancelled()).isFalse();
        cancelled.set(true);

        assertThat(scan.cancelled()).isTrue();
    }
}
