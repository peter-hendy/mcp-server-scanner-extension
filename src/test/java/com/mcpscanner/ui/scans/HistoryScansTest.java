package com.mcpscanner.ui.scans;

import com.mcpscanner.engine.ScanHistory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HistoryScansTest {

    private final ScanHistory history = mock(ScanHistory.class);
    private final ScansServices services = ScansServices.of(history, issue -> { }, mock(MessageEditors.class));

    @Test
    void feedsSnapshotsFromTheHistory() {
        when(history.snapshots()).thenReturn(List.of(ScanSnapshots.running(3)));
        Runnable listener = () -> { };

        services.feed().addListener(listener);
        services.feed().removeListener(listener);

        assertThat(services.feed().snapshots()).containsExactly(ScanSnapshots.running(3));
        verify(history).addListener(listener);
        verify(history).removeListener(listener);
    }

    @Test
    void controlsScansThroughTheHistory() {
        services.controls().pause(1);
        services.controls().resume(2);
        services.controls().cancel(3);
        services.controls().remove(4);
        services.controls().clearFinished();

        verify(history).pause(1);
        verify(history).resume(2);
        verify(history).cancel(3);
        verify(history).remove(4);
        verify(history).clearFinished();
    }
}
