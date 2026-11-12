package com.mcpscanner.checks.shared.scancontext;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class CurrentScanTest {

    @Test
    void namesTheScanOnlyOnTheInvocationThreadWithinTheScope() {
        AtomicLong seenElsewhere = new AtomicLong(-1);

        long seen = CurrentScan.within(Optional.of(new ScanContext(7)), () -> {
            Thread other = new Thread(() -> CurrentScan.get().ifPresent(context -> seenElsewhere.set(context.scanId())));
            other.start();
            join(other);
            return CurrentScan.get().orElseThrow().scanId();
        });

        assertThat(seen).isEqualTo(7);
        assertThat(seenElsewhere).hasValue(-1);
        assertThat(CurrentScan.get()).isEmpty();
    }

    @Test
    void runsWorkThatReturnsNothingOnlyOnce() {
        AtomicInteger runs = new AtomicInteger();

        CurrentScan.within(Optional.of(new ScanContext(3)), () -> {
            runs.incrementAndGet();
            return null;
        });

        assertThat(runs).hasValue(1);
    }

    @Test
    void anInnerScopeRestoresTheOuterScanEvenWhenItThrows() {
        ScanContext outer = new ScanContext(1);

        long seen = CurrentScan.within(Optional.of(outer), () -> {
            try {
                CurrentScan.within(Optional.empty(), () -> {
                    throw new IllegalStateException("inner");
                });
            } catch (IllegalStateException expected) {
                return CurrentScan.get().orElseThrow().scanId();
            }
            return -1L;
        });

        assertThat(seen).isEqualTo(1);
    }

    private static void join(Thread thread) {
        try {
            thread.join();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
