package com.mcpscanner.automation;

import com.mcpscanner.access.mining.MiningListener;
import com.mcpscanner.access.mining.MiningReport;
import com.mcpscanner.access.mining.MiningRun;
import com.mcpscanner.access.mining.MiningStop;
import com.mcpscanner.access.mining.WriteCallApproval;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HeadlessMiningTest {

    private final ExecutorService miningWorker = Executors.newSingleThreadExecutor();
    private final AtomicReference<WriteCallApproval> approvalGiven = new AtomicReference<>();

    @AfterEach
    void stopWorker() {
        miningWorker.shutdownNow();
    }

    @Test
    void returnsOnlyOnceMiningHasFinished() {
        MiningReport completed = new MiningReport(MiningStop.COMPLETED, List.of(), 2, 1);

        MiningReport report = new HeadlessMining(finishingWith(completed)).mineEveryUser();

        assertThat(report).isEqualTo(completed);
    }

    @Test
    void neverApprovesAWriteCall() {
        new HeadlessMining(finishingWith(new MiningReport(MiningStop.COMPLETED, List.of(), 0, 0))).mineEveryUser();

        assertThat(approvalGiven.get().approve("alice", List.of())).isFalse();
    }

    @Test
    void failsLoudlyWhenMiningDoesNotStart() {
        assertThatThrownBy(new HeadlessMining((listener, approval) -> Optional.empty())::mineEveryUser)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Collecting IDs did not start");
    }

    @Test
    void failsLoudlyWhenMiningDoesNotComplete() {
        MiningReport failed = new MiningReport(MiningStop.CANCELLED, List.of("mining failed: boom"), 0, 0);

        assertThatThrownBy(new HeadlessMining(finishingWith(failed))::mineEveryUser)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Collecting cancelled")
                .hasMessageContaining("mining failed: boom");
    }

    @Test
    void miningStoppedByRateLimitingIsAFailureToo() {
        MiningReport limited = new MiningReport(MiningStop.RATE_LIMITED, List.of(), 1, 0);

        assertThatThrownBy(new HeadlessMining(finishingWith(limited))::mineEveryUser)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("rate-limited");
    }

    private MiningStart finishingWith(MiningReport report) {
        return (MiningListener listener, WriteCallApproval approval) -> {
            approvalGiven.set(approval);
            miningWorker.execute(() -> listener.finished(report));
            return Optional.of(new MiningRun());
        };
    }
}
