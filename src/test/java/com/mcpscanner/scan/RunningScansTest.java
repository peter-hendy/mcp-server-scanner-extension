package com.mcpscanner.scan;

import com.mcpscanner.logging.McpEventLog;
import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class RunningScansTest {

    private final McpEventLog eventLog = new McpEventLog(null);
    private final RunningScans runningScans = new RunningScans(eventLog);

    @Test
    void activityCountsBurpAuditRequestsApartFromEngineProbesAndSumsIssues() {
        runningScans.track(FakeRun.burpAudit(3, 1));
        runningScans.track(FakeRun.engineRun(4, 2));

        assertThat(runningScans.activity()).isEqualTo(new ScanActivity(1, 3, 1, 4, 3));
    }

    @Test
    void finishedScansAreDroppedFromActivity() {
        FakeRun engineRun = runningScans.track(FakeRun.engineRun(4, 2));
        runningScans.track(FakeRun.burpAudit(3, 1));

        engineRun.finish();

        assertThat(runningScans.activity()).isEqualTo(ScanActivity.burpAudit(3, 1));
    }

    @Test
    void cancellingAllCancelsEveryScanAndForgetsThem() {
        FakeRun burpAudit = runningScans.track(FakeRun.burpAudit(0, 0));
        FakeRun engineRun = runningScans.track(FakeRun.engineRun(0, 0));

        runningScans.cancelAll();

        assertThat(burpAudit.cancelled()).isTrue();
        assertThat(engineRun.cancelled()).isTrue();
        assertThat(runningScans.activity().isIdle()).isTrue();
    }

    @Test
    void aScanThatFailsToCancelIsLoggedAndTheOthersAreStillCancelled() {
        runningScans.track(FakeRun.burpAudit(0, 0).failingToCancel());
        FakeRun engineRun = runningScans.track(FakeRun.engineRun(0, 0));

        runningScans.cancelAll();

        assertThat(engineRun.cancelled()).isTrue();
        assertThat(eventLog.snapshot()).anySatisfy(entry ->
                assertThat(entry.message()).isEqualTo("Scan cancel failed: IllegalStateException"));
    }

    @Test
    void clearingForgetsBurpAuditsWithoutCancellingThemBecauseTheDashboardStillOwnsThem() {
        FakeRun burpAudit = runningScans.track(FakeRun.burpAudit(0, 0));

        runningScans.clear();

        assertThat(burpAudit.cancelled()).isFalse();
        assertThat(runningScans.activity().isIdle()).isTrue();
    }

    @Test
    void clearingCancelsEngineRunsBecauseNothingElseCouldStopThem() {
        FakeRun engineRun = runningScans.track(FakeRun.engineRun(0, 0));

        runningScans.clear();

        assertThat(engineRun.cancelled()).isTrue();
        assertThat(runningScans.activity().isIdle()).isTrue();
    }

    @Test
    void scansClearedAwayAreNotCancelledAgainLater() {
        FakeRun burpAudit = runningScans.track(FakeRun.burpAudit(0, 0));
        runningScans.clear();

        runningScans.cancelAll();

        assertThat(burpAudit.cancelled()).isFalse();
    }

    private static final class FakeRun implements RunningScan {

        private final ScanActivity activity;
        private final boolean outlivesConnection;
        private boolean failsToCancel;
        private boolean cancelled;
        private boolean finished;

        private FakeRun(ScanActivity activity, boolean outlivesConnection) {
            this.activity = activity;
            this.outlivesConnection = outlivesConnection;
        }

        static FakeRun burpAudit(int requests, int issues) {
            return new FakeRun(ScanActivity.burpAudit(requests, issues), true);
        }

        static FakeRun engineRun(int probesRun, int issues) {
            return new FakeRun(ScanActivity.mcpOnlyScan(probesRun, issues), false);
        }

        FakeRun failingToCancel() {
            failsToCancel = true;
            return this;
        }

        void finish() {
            finished = true;
        }

        boolean cancelled() {
            return cancelled;
        }

        @Override
        public ScanActivity activity() {
            return activity;
        }

        @Override
        public boolean isFinished() {
            return finished;
        }

        @Override
        public boolean outlivesConnection() {
            return outlivesConnection;
        }

        @Override
        public void cancel() {
            if (failsToCancel) {
                throw new IllegalStateException("cancel failed");
            }
            cancelled = true;
        }

        @Override
        public void whenFinished(Consumer<ScanOutcome> listener) {
        }
    }
}
