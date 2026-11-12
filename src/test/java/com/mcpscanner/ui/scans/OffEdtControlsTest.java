package com.mcpscanner.ui.scans;

import com.mcpscanner.testutil.ManualReadScheduler;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OffEdtControlsTest {

    private final ManualReadScheduler scheduler = new ManualReadScheduler();
    private final FakeScans scans = new FakeScans();
    private final ScanControls controls = new OffEdtControls(scans, scheduler.reads());

    @Test
    void everyControlRunsOnTheReadThread() {
        controls.pause(1);
        controls.resume(1);
        controls.cancel(1);
        controls.remove(1);
        controls.clearFinished();

        assertThat(scans.calls).isEmpty();
        scheduler.runAll();
        assertThat(scans.calls).containsExactly("pause 1", "resume 1", "cancel 1", "remove 1", "clear finished");
    }
}
