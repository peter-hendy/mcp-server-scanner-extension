package com.mcpscanner.ui;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.client.TransportType;
import com.mcpscanner.proxy.observe.Direction;
import com.mcpscanner.proxy.observe.ExposureSurface;
import com.mcpscanner.proxy.observe.McpExchange;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class TrafficTableModelTest {

    private static final int COLUMN_TIME = 0;
    private static final int COLUMN_DIRECTION = 1;
    private static final int COLUMN_METHOD = 2;
    private static final int COLUMN_ID = 3;
    private static final int COLUMN_STATUS = 4;

    private final HttpRequest request = mock(HttpRequest.class);
    private final TrafficTableModel model = new TrafficTableModel();

    @Test
    void columnMetadataMatches() {
        assertThat(model.getColumnCount()).isEqualTo(5);
        assertThat(model.getColumnName(COLUMN_TIME)).isEqualTo("Time");
        assertThat(model.getColumnName(COLUMN_DIRECTION)).isEqualTo("Direction");
        assertThat(model.getColumnName(COLUMN_METHOD)).isEqualTo("Method");
        assertThat(model.getColumnName(COLUMN_ID)).isEqualTo("Id");
        assertThat(model.getColumnName(COLUMN_STATUS)).isEqualTo("Status");
    }

    @Test
    void startsEmpty() {
        assertThat(model.getRowCount()).isZero();
    }

    @Test
    void addExchangeAddsOneRow() {
        model.addExchange(exchange(Direction.CLIENT_TO_SERVER, "tools/call", "rpc-1", 200));

        assertThat(model.getRowCount()).isEqualTo(1);
    }

    @Test
    void rowRendersEveryCell() {
        Instant timing = Instant.parse("2026-06-29T09:30:15Z");
        model.addExchange(new McpExchange(
                "session-1", TransportType.STREAMABLE_HTTP, Direction.CLIENT_TO_SERVER,
                "rpc-7", 0, "tools/call", request, null, 200, timing,
                ExposureSurface.LIVE_RUNTIME_OUTPUT));

        assertThat((String) model.getValueAt(0, COLUMN_TIME)).isNotEmpty();
        assertThat(model.getValueAt(0, COLUMN_DIRECTION)).isEqualTo("C→S");
        assertThat(model.getValueAt(0, COLUMN_METHOD)).isEqualTo("tools/call");
        assertThat(model.getValueAt(0, COLUMN_ID)).isEqualTo("rpc-7");
        assertThat(model.getValueAt(0, COLUMN_STATUS)).isEqualTo("200");
    }

    @Test
    void serverToClientRendersInboundArrow() {
        model.addExchange(exchange(Direction.SERVER_TO_CLIENT, "tools/call", "rpc-1", 200));

        assertThat(model.getValueAt(0, COLUMN_DIRECTION)).isEqualTo("S→C");
    }

    @Test
    void nullStatusRendersAsEmptyStringNotNull() {
        model.addExchange(exchange(Direction.CLIENT_TO_SERVER, "tools/call", "rpc-1", null));

        assertThat(model.getValueAt(0, COLUMN_STATUS)).isEqualTo("");
    }

    @Test
    void nullMethodRendersAsEmptyStringNotNull() {
        model.addExchange(exchange(Direction.CLIENT_TO_SERVER, null, "rpc-1", 200));

        assertThat(model.getValueAt(0, COLUMN_METHOD)).isEqualTo("");
    }

    @Test
    void nullIdRendersAsEmptyStringNotNull() {
        model.addExchange(exchange(Direction.CLIENT_TO_SERVER, "tools/call", null, 200));

        assertThat(model.getValueAt(0, COLUMN_ID)).isEqualTo("");
    }

    @Test
    void nullTimingRendersAsEmptyStringNotNull() {
        model.addExchange(new McpExchange(
                "session-1", TransportType.STREAMABLE_HTTP, Direction.CLIENT_TO_SERVER,
                "rpc-1", 0, "tools/call", request, null, 200, null,
                ExposureSurface.LIVE_RUNTIME_OUTPUT));

        assertThat(model.getValueAt(0, COLUMN_TIME)).isEqualTo("");
    }

    @Test
    void setExchangesReplacesAllRows() {
        model.addExchange(exchange(Direction.CLIENT_TO_SERVER, "old", "rpc-1", 200));

        model.setExchanges(List.of(
                exchange(Direction.CLIENT_TO_SERVER, "tools/list", "rpc-2", 200),
                exchange(Direction.SERVER_TO_CLIENT, "tools/list", "rpc-2", 200)));

        assertThat(model.getRowCount()).isEqualTo(2);
        assertThat(model.getValueAt(0, COLUMN_METHOD)).isEqualTo("tools/list");
        assertThat(model.getValueAt(1, COLUMN_DIRECTION)).isEqualTo("S→C");
    }

    @Test
    void everyColumnIsString() {
        for (int column = 0; column < model.getColumnCount(); column++) {
            assertThat(model.getColumnClass(column)).isEqualTo(String.class);
        }
    }

    private McpExchange exchange(Direction direction, String method, String jsonrpcId, Integer status) {
        return new McpExchange(
                "session-1", TransportType.STREAMABLE_HTTP, direction, jsonrpcId, 0,
                method, request, null, status, Instant.parse("2026-06-29T00:00:00Z"),
                ExposureSurface.LIVE_RUNTIME_OUTPUT);
    }
}
