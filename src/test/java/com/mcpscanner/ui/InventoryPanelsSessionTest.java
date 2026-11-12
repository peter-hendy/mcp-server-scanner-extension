package com.mcpscanner.ui;

import com.mcpscanner.client.ConnectResult;
import com.mcpscanner.client.TransportType;
import com.mcpscanner.mcp.ServerMetadata;
import com.mcpscanner.ui.detail.DetailSession;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryPanelsSessionTest {

    private static final Map<String, Object> CAPABILITIES = Map.of("completions", Map.of());
    private static final DetailSession EXPECTED = new DetailSession("2025-11-25", CAPABILITIES);

    private final InventoryPanels inventory = new InventoryPanels();

    @BeforeEach
    void setUp() {
        MontoyaTestFactory.install();
    }

    @Test
    void capabilitiesThenConnectionReachEveryDetailPanel() {
        inventory.populate(connectResult());
        inventory.showConnection(connection());

        assertEveryDetailPanelSees(EXPECTED);
    }

    @Test
    void connectionThenCapabilitiesReachEveryDetailPanel() {
        inventory.showConnection(connection());
        inventory.populate(connectResult());

        assertEveryDetailPanelSees(EXPECTED);
    }

    @Test
    void theConnectionAlsoReachesServerInfo() {
        inventory.showConnection(connection());

        assertThat(InfoSections.titled(inventory.serverInfo(), "Session").getText()).contains("2025-11-25");
    }

    @Test
    void clearingResetsTheSession() {
        inventory.populate(connectResult());
        inventory.showConnection(connection());

        inventory.clear();

        assertEveryDetailPanelSees(DetailSession.none());
    }

    private void assertEveryDetailPanelSees(DetailSession session) {
        assertThat(inventory.tools().detailPanel().session()).isEqualTo(session);
        assertThat(inventory.resources().detailPanel().session()).isEqualTo(session);
        assertThat(inventory.templates().detailPanel().session()).isEqualTo(session);
        assertThat(inventory.prompts().detailPanel().session()).isEqualTo(session);
    }

    private static ConnectResult connectResult() {
        return new ConnectResult(List.of(), List.of(), List.of(), List.of(),
                new ServerMetadata(Map.of(), "", CAPABILITIES));
    }

    private static ConnectionSummary connection() {
        return new ConnectionSummary(TransportType.AUTO, TransportType.STREAMABLE_HTTP, "2025-11-25",
                "http://localhost/mcp", "None");
    }
}
