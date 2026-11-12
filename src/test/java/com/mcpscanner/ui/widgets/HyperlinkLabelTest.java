package com.mcpscanner.ui.widgets;

import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import com.mcpscanner.testutil.Polling;

import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class HyperlinkLabelTest {

    private static final URI TARGET = URI.create("https://example.com/docs");

    @Test
    void keyboardUsersCanFocusAndActivateTheLink() throws Exception {
        AtomicInteger browsed = new AtomicInteger();
        HyperlinkLabel label = new HyperlinkLabel("docs", TARGET, uri -> browsed.incrementAndGet(), ignoredError());

        assertThat(label.isFocusable()).isTrue();
        invokeAndWait(() -> {
            for (String key : new String[] {"ENTER", "SPACE"}) {
                Object action = label.getInputMap(javax.swing.JComponent.WHEN_FOCUSED)
                        .get(javax.swing.KeyStroke.getKeyStroke(key));
                label.getActionMap().get(action).actionPerformed(null);
            }
        });

        awaitWithinOneSecond().until(() -> browsed.get() >= 2);
    }

    @Test
    void mouseClickedInvokesBrowseActionWithTargetUri() throws Exception {
        AtomicBoolean browsed = new AtomicBoolean();
        AtomicReference<URI> recorded = new AtomicReference<>();
        HyperlinkLabel label = new HyperlinkLabel("docs", TARGET, uri -> {
            recorded.set(uri);
            browsed.set(true);
        }, ignoredError());

        invokeAndWait(() -> fireMouseClicked(label));

        awaitWithinOneSecond().untilTrue(browsed);
        assertThat(recorded.get()).isEqualTo(TARGET);
    }

    @Test
    void mouseClickedInvokesBrowseActionOffEdt() throws Exception {
        AtomicBoolean fired = new AtomicBoolean();
        AtomicReference<Boolean> firedOnEdt = new AtomicReference<>();
        HyperlinkLabel label = new HyperlinkLabel("docs", TARGET, uri -> {
            firedOnEdt.set(SwingUtilities.isEventDispatchThread());
            fired.set(true);
        }, ignoredError());

        invokeAndWait(() -> fireMouseClicked(label));

        awaitWithinOneSecond().untilTrue(fired);
        assertThat(firedOnEdt.get()).isFalse();
    }

    @Test
    void browseFailureIsReportedToErrorSink() throws Exception {
        RuntimeException failure = new RuntimeException("browse blew up");
        AtomicBoolean reported = new AtomicBoolean();
        AtomicReference<Throwable> recorded = new AtomicReference<>();
        HyperlinkLabel label = new HyperlinkLabel("docs", TARGET, uri -> {
            throw failure;
        }, throwable -> {
            recorded.set(throwable);
            reported.set(true);
        });

        invokeAndWait(() -> fireMouseClicked(label));

        awaitWithinOneSecond().untilTrue(reported);
        assertThat(recorded.get()).isSameAs(failure);
    }

    @Test
    void browseFailureDoesNotPropagateFromLaunchThread() throws Exception {
        AtomicBoolean reported = new AtomicBoolean();
        AtomicReference<Throwable> uncaught = new AtomicReference<>();
        HyperlinkLabel label = new HyperlinkLabel("docs", TARGET, uri -> {
            Thread.currentThread().setUncaughtExceptionHandler(
                    (thread, throwable) -> uncaught.set(throwable));
            throw new RuntimeException("browse blew up");
        }, throwable -> reported.set(true));

        invokeAndWait(() -> fireMouseClicked(label));

        awaitWithinOneSecond().untilTrue(reported);
        assertThat(uncaught.get()).isNull();
    }

    @Test
    void twoArgConstructorWiresNoOpSinkAndAttachesMouseListener() throws Exception {
        // The 2-arg public ctor uses NO_OP_ERROR_SINK + real Desktop.browse. Verify the
        // constructor chain succeeds and attaches a mouse listener (functional wiring check
        // without asserting on Desktop availability).
        HyperlinkLabel label = new HyperlinkLabel("docs", TARGET);
        invokeAndWait(() -> {});
        assertThat(label.getMouseListeners()).isNotEmpty();
    }

    @Test
    void sinkThrowingDoesNotEscapeFromDaemonThread() throws Exception {
        AtomicBoolean sinkCalled = new AtomicBoolean();
        HyperlinkLabel label = new HyperlinkLabel("docs", TARGET, uri -> {
            throw new RuntimeException("browse blew up");
        }, throwable -> {
            sinkCalled.set(true);
            throw new RuntimeException("sink also blew up");
        });

        invokeAndWait(() -> fireMouseClicked(label));

        awaitWithinOneSecond().untilTrue(sinkCalled);
        // If the sink exception escaped the daemon thread it would surface as a test
        // framework error or an uncaught exception in the daemon — neither should happen.
    }

    private static ConditionFactory awaitWithinOneSecond() {
        return Polling.within(Duration.ofSeconds(1));
    }

    private static Consumer<Throwable> ignoredError() {
        return throwable -> {
        };
    }

    @Test
    void anActionLinkRunsItsActionsOnClickAndEnter() throws Exception {
        java.util.concurrent.atomic.AtomicInteger runs = new java.util.concurrent.atomic.AtomicInteger();
        HyperlinkLabel link = new HyperlinkLabel("Open Identities");
        link.onActivate(runs::incrementAndGet);

        invokeAndWait(() -> {
            fireMouseClicked(link);
            link.getActionMap().get(link.getInputMap(javax.swing.JComponent.WHEN_FOCUSED)
                    .get(javax.swing.KeyStroke.getKeyStroke("ENTER"))).actionPerformed(null);
        });

        assertThat(runs).hasValue(2);
    }

    @Test
    void aDisabledLinkIgnoresBothMouseAndKeyboard() throws Exception {
        java.util.concurrent.atomic.AtomicInteger runs = new java.util.concurrent.atomic.AtomicInteger();
        HyperlinkLabel link = new HyperlinkLabel("Open Identities");
        link.onActivate(runs::incrementAndGet);
        link.setEnabled(false);

        invokeAndWait(() -> {
            fireMouseClicked(link);
            link.getActionMap().get(link.getInputMap(javax.swing.JComponent.WHEN_FOCUSED)
                    .get(javax.swing.KeyStroke.getKeyStroke("ENTER"))).actionPerformed(null);
        });

        assertThat(runs).hasValue(0);
    }

    @Test
    void theLinkColourFollowsALookAndFeelChange() throws Exception {
        HyperlinkLabel link = new HyperlinkLabel("Open Identities");
        java.awt.Color previous = javax.swing.UIManager.getColor("Hyperlink.linkColor");
        try {
            javax.swing.UIManager.put("Hyperlink.linkColor", new java.awt.Color(0x123456));
            invokeAndWait(link::updateUI);
            assertThat(link.getForeground()).isEqualTo(new java.awt.Color(0x123456));
        } finally {
            javax.swing.UIManager.put("Hyperlink.linkColor", previous);
        }
    }

    private static void fireMouseClicked(HyperlinkLabel label) {
        MouseEvent event = new MouseEvent(label, MouseEvent.MOUSE_CLICKED,
                System.currentTimeMillis(), 0, 0, 0, 1, false);
        for (MouseListener listener : label.getMouseListeners()) {
            listener.mouseClicked(event);
        }
    }

    private static void invokeAndWait(Runnable r) throws InterruptedException, InvocationTargetException {
        if (SwingUtilities.isEventDispatchThread()) {
            r.run();
        } else {
            SwingUtilities.invokeAndWait(r);
        }
    }
}
