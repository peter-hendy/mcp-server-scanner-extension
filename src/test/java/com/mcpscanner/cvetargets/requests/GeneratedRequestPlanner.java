package com.mcpscanner.cvetargets.requests;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.client.ConnectResult;
import com.mcpscanner.cvetargets.manifest.SeedRequest;
import com.mcpscanner.mcp.McpHeaders;
import com.mcpscanner.mcp.McpOperationFraming;
import com.mcpscanner.scan.ScanInventory;
import com.mcpscanner.scan.field.TemplateExpansionRegistry;
import com.mcpscanner.scan.request.ClientRegistrationRequestFactory;
import com.mcpscanner.scan.request.McpHttpRequestFactory;
import com.mcpscanner.scan.request.RequestGenerator;
import com.mcpscanner.scan.request.ScanMessage;
import com.mcpscanner.scan.request.ScanRequestRenderer;
import com.mcpscanner.scan.request.ServerCapabilities;
import com.mcpscanner.scan.request.ServerSurface;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

public final class GeneratedRequestPlanner implements BaseRequestPlanner {

    private static final long FIRST_REQUEST_ID = 1000;

    private final TemplateExpansionRegistry templateExpansions;
    private final RequestGenerator generator = RequestGenerator.standard();
    private final AtomicLong requestIds = new AtomicLong(FIRST_REQUEST_ID);
    private final JsonRpcBaseRequests seedRequests = new JsonRpcBaseRequests(requestIds::incrementAndGet);

    public GeneratedRequestPlanner(TemplateExpansionRegistry templateExpansions) {
        this.templateExpansions = templateExpansions;
    }

    @Override
    public List<HttpRequest> plan(ConnectResult inventory, List<SeedRequest> seeds, String endpoint,
                                  Map<String, String> sessionHeaders, McpOperationFraming.Framer framer) {
        ScanRequestRenderer renderer = renderer(framer);
        List<ScanMessage> messages = generator.scanAll(surfaceOf(inventory, negotiatedVersion(sessionHeaders)), family -> true).messages();
        messages.forEach(message -> message.templateExpansion().ifPresent(templateExpansions::record));
        return Stream.concat(
                        messages.stream().map(message -> message.renderWith(renderer, endpoint, sessionHeaders)),
                        seeds.stream().map(seed -> seedRequests.build(endpoint, seed.routing().headers(sessionHeaders),
                                new JsonRpcCall(seed.method(), seed.params()))))
                .toList();
    }

    private ScanRequestRenderer renderer(McpOperationFraming.Framer framer) {
        return new ScanRequestRenderer(
                new HarnessRequestRenderer(new McpHttpRequestFactory(requestIds::incrementAndGet, framer)),
                new ClientRegistrationRequestFactory());
    }

    private static String negotiatedVersion(Map<String, String> sessionHeaders) {
        return sessionHeaders.entrySet().stream()
                .filter(header -> McpHeaders.PROTOCOL_VERSION.equalsIgnoreCase(header.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private static ServerSurface surfaceOf(ConnectResult inventory, String protocolVersion) {
        Map<String, Object> capabilities = inventory.serverMetadata() == null
                ? Map.of()
                : inventory.serverMetadata().capabilities();
        return new ServerSurface(new ScanInventory(inventory.tools(), inventory.resources(),
                inventory.resourceTemplates(), inventory.prompts()), new ServerCapabilities(capabilities),
                protocolVersion);
    }
}
