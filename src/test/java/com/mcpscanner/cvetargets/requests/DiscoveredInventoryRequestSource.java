package com.mcpscanner.cvetargets.requests;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.HttpService;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.logging.Logging;
import com.mcpscanner.client.ConnectResult;
import com.mcpscanner.client.McpClientManager;
import com.mcpscanner.client.McpScannerSession;
import com.mcpscanner.cvetargets.manifest.SeedRequest;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.mcp.McpOperationFraming;
import com.mcpscanner.mcp.SseProbeStreams;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.mockito.Mockito.mock;

public final class DiscoveredInventoryRequestSource implements BaseRequestSource {

    private static final int CONNECT_ATTEMPTS = 3;

    private final BaseRequestPlanner planner;

    public DiscoveredInventoryRequestSource(BaseRequestPlanner planner) {
        this.planner = planner;
    }

    @Override
    public BaseRequestSet open(ArmEndpoint endpoint, BaseRequestScope scope, Http http) {
        List<String> failedAttempts = new ArrayList<>();
        while (failedAttempts.size() < CONNECT_ATTEMPTS) {
            ConnectedRequests connection = ConnectedRequests.create();
            try {
                ConnectResult inventory = connection.manager().connect(endpoint.serverConfig());
                McpOperationFraming.Framer framer = InventoryFraming.withParamHeaders(
                        connection.manager().scannerSession(), inventory);
                connection = connection.withRoute(TransportRoute.forSession(connection.manager().scannerSession(), framer,
                        InventoryFraming.resendable(inventory)));
                List<HttpRequestResponse> sent = send(scope.limit(inventory), scope.seeds(), connection, http, framer);
                return connection.withRequests(sent.stream().filter(exchange -> Objects.nonNull(exchange.response())).toList(),
                        (int) sent.stream().filter(exchange -> Objects.isNull(exchange.response())).count());
            } catch (RuntimeException connectFailure) {
                failedAttempts.add(connectFailure.getMessage() + " [extension event log: " + describe(connection.eventLog()) + "]");
                connection.close();
            }
        }
        throw new IllegalStateException("connect failed " + CONNECT_ATTEMPTS + " times: " + String.join(" ; ", failedAttempts));
    }

    private List<HttpRequestResponse> send(ConnectResult inventory, List<SeedRequest> seeds,
                                           ConnectedRequests connection, Http http, McpOperationFraming.Framer framer) {
        McpScannerSession session = connection.manager().scannerSession();
        Http routed = connection.route(http);
        return planner.plan(inventory, seeds, session.resolvedEndpoint(),
                        connection.transportRoute().sessionHeaders(session.scannerHeaders()), framer).stream()
                .map(routed::sendRequest)
                .toList();
    }

    private static String describe(McpEventLog eventLog) {
        return eventLog.snapshot().stream()
                .map(entry -> entry.level() + " " + entry.message())
                .collect(Collectors.joining(" | "));
    }

    private record ConnectedRequests(McpClientManager manager, McpEventLog eventLog, TransportRoute transportRoute,
                                     List<HttpRequestResponse> requests, int unansweredCount)
            implements BaseRequestSet {

        static ConnectedRequests create() {
            McpEventLog eventLog = new McpEventLog(mock(Logging.class));
            return new ConnectedRequests(new McpClientManager(eventLog), eventLog,
                    TransportRoute.DIRECT, List.of(), 0);
        }

        ConnectedRequests withRoute(TransportRoute route) {
            return new ConnectedRequests(manager, eventLog, route, requests, unansweredCount);
        }

        ConnectedRequests withRequests(List<HttpRequestResponse> answered, int unanswered) {
            return new ConnectedRequests(manager, eventLog, transportRoute, answered, unanswered);
        }

        @Override
        public Http route(Http http) {
            return transportRoute.route(http);
        }

        @Override
        public boolean reachedTarget(HttpRequestResponse exchange) {
            return transportRoute.reachedTarget(exchange);
        }

        @Override
        public Optional<HttpService> probeService() {
            return transportRoute.probeService();
        }

        @Override
        public Optional<SseProbeStreams> sseProbeStreams() {
            return transportRoute.sseProbeStreams();
        }

        @Override
        public void close() {
            try (TransportRoute route = transportRoute) {
                manager.disconnect();
            } finally {
                eventLog.shutdown();
            }
        }
    }
}
