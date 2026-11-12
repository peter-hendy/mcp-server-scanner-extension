package com.mcpscanner.ui;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class PendingReconnectTest {

    private final AtomicBoolean disconnected = new AtomicBoolean(true);
    private final AtomicInteger connects = new AtomicInteger();
    private final PendingReconnect pending = new PendingReconnect(disconnected::get, connects::incrementAndGet);

    @Test
    void aRequestedReconnectConnectsExactlyOnce() {
        Runnable reconnect = pending.request();

        reconnect.run();
        reconnect.run();

        assertThat(connects).hasValue(1);
    }

    @Test
    void aDiscardedReconnectNeverConnects() {
        Runnable reconnect = pending.request();

        pending.discard();
        reconnect.run();

        assertThat(connects).hasValue(0);
    }

    @Test
    void aReconnectIsDroppedWhenTheConnectionIsNoLongerDisconnected() {
        Runnable reconnect = pending.request();
        disconnected.set(false);

        reconnect.run();
        disconnected.set(true);
        reconnect.run();

        assertThat(connects).hasValue(0);
    }

    @Test
    void aNewerRequestSupersedesAnOlderOne() {
        Runnable stale = pending.request();
        Runnable current = pending.request();

        stale.run();
        assertThat(connects).hasValue(0);
        current.run();
        assertThat(connects).hasValue(1);
    }
}
