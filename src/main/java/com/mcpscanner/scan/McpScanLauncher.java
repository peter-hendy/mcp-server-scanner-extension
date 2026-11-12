package com.mcpscanner.scan;

import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.logging.Logging;
import com.mcpscanner.scan.field.TemplateExpansionRegistry;
import com.mcpscanner.scan.request.GeneratedScan;
import com.mcpscanner.scan.request.ScanMessage;
import com.mcpscanner.scan.request.ScanMessageRenderer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

public class McpScanLauncher {

    private final Logging logging;
    private final ScanMessageRenderer requestRenderer;
    private final Supplier<ScanConnection> connectionFactory;
    private final TemplateExpansionRegistry templateExpansions;
    private final ScanDestinations destinations;
    private final RunningScans runningScans;

    public McpScanLauncher(Logging logging, ScanMessageRenderer requestRenderer,
                           Supplier<ScanConnection> connectionFactory, TemplateExpansionRegistry templateExpansions,
                           ScanDestinations destinations, RunningScans runningScans) {
        this.logging = Objects.requireNonNull(logging);
        this.requestRenderer = Objects.requireNonNull(requestRenderer);
        this.connectionFactory = Objects.requireNonNull(connectionFactory);
        this.templateExpansions = Objects.requireNonNull(templateExpansions);
        this.destinations = Objects.requireNonNull(destinations);
        this.runningScans = Objects.requireNonNull(runningScans);
    }

    public ScanConnection captureConnection() {
        return connectionFactory.get();
    }

    public Supplier<RunningScan> captureScan(ScanMode mode, String endpoint, GeneratedScan scan,
                                             Supplier<Map<String, String>> sessionHeaders, ScanConnection connection) {
        Supplier<Map<String, String>> headers = connectionHeaders(sessionHeaders, connection);
        return () -> launchScan(mode, endpoint, scan, headers.get());
    }

    public RunningScan launchScan(ScanMode mode, String endpoint, GeneratedScan scan, Map<String, String> headers) {
        ScanDestination destination = destinations.forMode(mode);
        return runningScans.track(destination.start(render(destination, endpoint, scan, headers)));
    }

    public boolean waitsForPausedTasks(ScanMode mode) {
        return destinations.forMode(mode).waitsForPausedTasks();
    }

    public void resumePausedTasks(ScanMode mode) {
        destinations.forMode(mode).resumePausedTasks();
    }

    public ScanActivity activity() {
        return runningScans.activity();
    }

    public void cancelRunningScans() {
        runningScans.cancelAll();
    }

    public void clearRunningScans() {
        runningScans.clear();
    }

    private static Supplier<Map<String, String>> connectionHeaders(Supplier<Map<String, String>> sessionHeaders,
                                                                   ScanConnection connection) {
        return () -> {
            connection.requireCurrent();
            Map<String, String> headers = new LinkedHashMap<>(sessionHeaders.get());
            headers.putAll(connection.headers(Map.copyOf(headers)));
            connection.requireCurrent();
            return headers;
        };
    }

    private List<HttpRequest> render(ScanDestination destination, String endpoint, GeneratedScan scan,
                                     Map<String, String> headers) {
        return scan.messages().stream().map(message -> render(destination, message, endpoint, headers)).toList();
    }

    private HttpRequest render(ScanDestination destination, ScanMessage message, String endpoint,
                               Map<String, String> headers) {
        message.templateExpansion().ifPresent(templateExpansions::record);
        HttpRequest request = message.renderWith(requestRenderer, endpoint, headers);
        logging.logToOutput("Added " + message.subject() + " to " + destination.name());
        return request;
    }
}
