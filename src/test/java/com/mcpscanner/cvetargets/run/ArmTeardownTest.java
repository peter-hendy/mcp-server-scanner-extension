package com.mcpscanner.cvetargets.run;

import com.mcpscanner.cvetargets.environment.RunningArm;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArmTeardownTest {

    private final AtomicInteger closes = new AtomicInteger();
    private final RunningArm arm = new RunningArm() {
        @Override
        public URI endpoint() {
            return URI.create("http://127.0.0.1:1/mcp");
        }

        @Override
        public void close() {
            closes.incrementAndGet();
        }
    };

    @Test
    void closesTheHeldArmExactlyOnce() {
        ArmTeardown teardown = new ArmTeardown();
        assertThat(teardown.hold(arm)).isSameAs(arm);

        teardown.close();
        teardown.close();

        assertThat(closes).hasValue(1);
    }

    @Test
    void closesTheArmUninterruptedAndRestoresTheCallersInterrupt() {
        AtomicBoolean interruptedDuringClose = new AtomicBoolean(true);
        ArmTeardown teardown = new ArmTeardown();
        teardown.hold(new RunningArm() {
            @Override
            public URI endpoint() {
                return URI.create("http://127.0.0.1:1/mcp");
            }

            @Override
            public void close() {
                interruptedDuringClose.set(Thread.currentThread().isInterrupted());
            }
        });

        Thread.currentThread().interrupt();
        teardown.close();

        assertThat(interruptedDuringClose).isFalse();
        assertThat(Thread.interrupted()).isTrue();
    }

    @Test
    void reportsWhetherItHasClosed() {
        ArmTeardown teardown = new ArmTeardown();
        assertThat(teardown.isClosed()).isFalse();

        teardown.close();

        assertThat(teardown.isClosed()).isTrue();
    }

    @Test
    void closingBeforeAnyArmStartedDoesNothing() {
        new ArmTeardown().close();

        assertThat(closes).hasValue(0);
    }

    @Test
    void anArmThatStartsAfterTeardownIsClosedAtOnce() {
        ArmTeardown teardown = new ArmTeardown();
        teardown.close();

        assertThatThrownBy(() -> teardown.hold(arm)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("torn down");
        assertThat(closes).hasValue(1);
    }
}
