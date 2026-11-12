package com.mcpscanner.ui.widgets;

import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class EdtTest {

    @Test
    void onTheEdtTheTaskRunsImmediately() throws Exception {
        AtomicBoolean ranBeforeReturn = new AtomicBoolean();
        SwingUtilities.invokeAndWait(() -> {
            AtomicBoolean ran = new AtomicBoolean();
            Edt.run(() -> ran.set(true));
            ranBeforeReturn.set(ran.get());
        });

        assertThat(ranBeforeReturn).isTrue();
    }

    @Test
    void offTheEdtTheTaskRunsOnTheEdt() throws Exception {
        AtomicBoolean onEdt = new AtomicBoolean();
        AtomicBoolean ran = new AtomicBoolean();

        Edt.run(() -> {
            onEdt.set(SwingUtilities.isEventDispatchThread());
            ran.set(true);
        });

        await().atMost(Duration.ofSeconds(2)).pollInterval(Duration.ofMillis(10)).untilTrue(ran);
        assertThat(onEdt).isTrue();
    }
}
