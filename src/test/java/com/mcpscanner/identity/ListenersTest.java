package com.mcpscanner.identity;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ListenersTest {

    private final Listeners listeners = new Listeners();
    private final AtomicInteger notified = new AtomicInteger();

    @Test
    void firingNotifiesEveryAddedListener() {
        listeners.add(notified::incrementAndGet);
        listeners.add(notified::incrementAndGet);

        listeners.fire();

        assertThat(notified).hasValue(2);
    }

    @Test
    void aRemovedListenerIsNoLongerNotified() {
        Runnable listener = notified::incrementAndGet;
        listeners.add(listener);
        listeners.remove(listener);

        listeners.fire();

        assertThat(notified).hasValue(0);
    }
}
