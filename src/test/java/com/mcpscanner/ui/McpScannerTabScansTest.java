package com.mcpscanner.ui;

import com.mcpscanner.engine.ScanErrors;
import com.mcpscanner.engine.ScanFindings;
import com.mcpscanner.engine.ScanLabel;
import com.mcpscanner.engine.ScanProgress;
import com.mcpscanner.engine.ScanSnapshot;
import com.mcpscanner.engine.ScanStatus;
import com.mcpscanner.ui.scans.MessageEditors;
import com.mcpscanner.ui.scans.ScanControls;
import com.mcpscanner.ui.scans.ScanFeed;
import com.mcpscanner.ui.scans.ScansServices;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

import static com.mcpscanner.ui.McpScannerTabFixture.find;
import static com.mcpscanner.ui.McpScannerTabFixture.fromEdt;
import static com.mcpscanner.ui.McpScannerTabFixture.inventory;
import static com.mcpscanner.ui.McpScannerTabFixture.onEdt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class McpScannerTabScansTest {

    private static final ScanSnapshot RUNNING = new ScanSnapshot(1,
            new ScanLabel("Scan 2 requests", "mcp.example", "SSE", "User 1", "http://mcp.example"), ScanStatus.RUNNING,
            Instant.parse("2026-11-11T10:00:00Z"), Optional.empty(), new ScanProgress(4, 1, 0, 0, false), 2,
            new ScanErrors(List.of(), 0), List.of(), new ScanFindings(List.of(), 0, Map.of()));

    private final McpScannerTabFixture fixture = new McpScannerTabFixture();
    private final ScanFeed board = mock(ScanFeed.class);

    @Test
    void theScansTabSitsAmongTheInventoryTabsAndCountsRunningScans() throws Exception {
        when(board.snapshots()).thenReturn(List.of(RUNNING));

        McpScannerTab tab = fixture.connected(inventory(List.of()), this::install);
        InventoryTabs tabs = fromEdt(() -> find(tab, InventoryTabs.class));

        assertThat(titles(tabs)).last().isEqualTo("Scans (1 running)");
    }

    @Test
    void shutdownStopsTheScansTabListening() throws Exception {
        when(board.snapshots()).thenReturn(List.of());
        McpScannerTab tab = fixture.connected(inventory(List.of()), this::install);
        ArgumentCaptor<Runnable> listener = ArgumentCaptor.forClass(Runnable.class);
        verify(board).addListener(listener.capture());

        onEdt(tab::shutdown);

        verify(board).removeListener(listener.getValue());
    }

    private void install(McpScannerTab tab) {
        tab.installScans(new ScansServices(board, mock(ScanControls.class), location -> { }, mock(MessageEditors.class)));
    }

    private static List<String> titles(InventoryTabs tabs) throws Exception {
        return fromEdt(() -> IntStream.range(0, tabs.getTabCount()).mapToObj(tabs::getTitleAt).toList());
    }
}
