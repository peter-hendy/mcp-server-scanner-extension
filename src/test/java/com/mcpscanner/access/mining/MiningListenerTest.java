package com.mcpscanner.access.mining;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MiningListenerTest {

    @Test
    void onFinishedHandsOverTheReportAndIgnoresProgress() {
        List<MiningReport> reports = new ArrayList<>();
        MiningReport report = new MiningReport(MiningStop.COMPLETED, List.of(), 1, 0);
        MiningListener listener = MiningListener.onFinished(reports::add);

        listener.progressed("alice", new MiningStep("alice", 1, 2));
        listener.finished(report);

        assertThat(reports).containsExactly(report);
    }
}
