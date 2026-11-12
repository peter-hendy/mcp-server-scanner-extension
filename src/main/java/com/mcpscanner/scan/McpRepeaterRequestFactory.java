package com.mcpscanner.scan;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.scan.request.RequestFamily;
import com.mcpscanner.scan.request.RequestGenerator;
import com.mcpscanner.scan.request.ScanMessage;
import com.mcpscanner.scan.request.ScanMessageRenderer;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Supplier;

public final class McpRepeaterRequestFactory {

    private final ScanSession session;
    private final RequestGenerator generator;
    private final ScanMessageRenderer renderer;
    private final Supplier<ScanConnection> connectionSupplier;

    public McpRepeaterRequestFactory(ScanSession session, RequestGenerator generator, ScanMessageRenderer renderer,
                                     Supplier<ScanConnection> connectionSupplier) {
        this.session = Objects.requireNonNull(session);
        this.generator = Objects.requireNonNull(generator);
        this.renderer = Objects.requireNonNull(renderer);
        this.connectionSupplier = Objects.requireNonNull(connectionSupplier);
    }

    public HttpRequest requestFor(ScanTarget target) {
        return render(target.family(), target.inventory());
    }

    public HttpRequest requestFor(RequestFamily family) {
        return render(family, ScanInventory.toolsOnly(List.of()));
    }

    private HttpRequest render(RequestFamily family, ScanInventory inventory) {
        return requestFor(generator.scanThisRequest(family, inventory).messages().get(0));
    }

    public HttpRequest requestFor(ScanMessage message) {
        ScanConnection connection = connectionSupplier.get();
        connection.requireCurrent();
        HttpRequest request = message.renderWith(renderer, endpoint(), session.scannerHeaders());
        HttpRequest withConnectionHeaders = applyConnectionHeaders(request, connection);
        connection.requireCurrent();
        return withConnectionHeaders;
    }

    private HttpRequest applyConnectionHeaders(HttpRequest request, ScanConnection connection) {
        Map<String, String> wireHeaders = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        request.headers().forEach(header -> wireHeaders.put(header.name(), header.value()));
        for (var header : connection.headers(Map.copyOf(wireHeaders)).entrySet()) {
            request = wireHeaders.containsKey(header.getKey())
                    ? request.withUpdatedHeader(header.getKey(), header.getValue())
                    : request.withAddedHeader(header.getKey(), header.getValue());
            wireHeaders.put(header.getKey(), header.getValue());
        }
        return request;
    }

    private String endpoint() {
        String endpoint = session.resolvedEndpoint();
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalStateException("MCP server is not connected");
        }
        return endpoint;
    }
}
