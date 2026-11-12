package com.mcpscanner.ui.connect;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ConnectionFailureHandlerTest {
    @Test
    void currentConnectionFailureRunsThroughTheDispatcher() {
        var events = new ArrayDeque<Runnable>();
        var cancelled = new AtomicBoolean();
        var handler = new ConnectionFailureHandler(events::add, () -> cancelled.set(true));
        handler.beginConnection().run();
        assertThat(cancelled).isFalse();
        events.remove().run();
        assertThat(cancelled).isTrue();
    }

    @Test
    void retiredConnectionFailureIsIgnoredBeforeEnqueue() {
        var events = new ArrayDeque<Runnable>();
        var handler = new ConnectionFailureHandler(events::add,
                () -> { throw new AssertionError("Retired connection must not change the current connection"); });
        Runnable failedA = handler.beginConnection();
        handler.beginConnection();
        failedA.run();
        assertThat(events).isEmpty();
    }

    @Test
    void failureQueuedBeforeReconnectCannotCancelNewAuditOrReplaceItsStatus() {
        var events = new ArrayDeque<Runnable>();
        var auditActive = new AtomicBoolean();
        var status = new AtomicReference<>("A connected");
        var handler = new ConnectionFailureHandler(events::add, () -> {
            auditActive.set(false);
            status.set("OAuth failed");
        });
        Runnable failedA = handler.beginConnection();
        failedA.run();
        Runnable failedB = handler.beginConnection();
        auditActive.set(true);
        status.set("B connected");
        events.remove().run();
        assertThat(auditActive).isTrue();
        assertThat(status).hasValue("B connected");
        failedB.run();
        events.remove().run();
        assertThat(auditActive).isFalse();
        assertThat(status).hasValue("OAuth failed");
    }

    @Test
    void disconnectRetiresQueuedAndSubsequentFailures() {
        var events = new ArrayDeque<Runnable>();
        var cancelled = new AtomicBoolean();
        var handler = new ConnectionFailureHandler(events::add, () -> cancelled.set(true));
        Runnable failed = handler.beginConnection();
        failed.run();
        handler.retireConnection();
        events.remove().run();
        failed.run();
        assertThat(cancelled).isFalse();
        assertThat(events).isEmpty();
    }
}
