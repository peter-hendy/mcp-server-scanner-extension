package com.mcpscanner.cvetargets.mining;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.client.ConnectResult;
import com.mcpscanner.cvetargets.manifest.SeedRequest;
import com.mcpscanner.cvetargets.requests.BaseRequestPlanner;
import com.mcpscanner.cvetargets.requests.HarnessRequestRenderer;
import com.mcpscanner.cvetargets.requests.JsonRpcBaseRequests;
import com.mcpscanner.cvetargets.requests.JsonRpcCall;
import com.mcpscanner.mcp.McpOperationFraming;
import com.mcpscanner.scan.request.McpHttpRequestFactory;
import com.mcpscanner.scan.ScanInventory;
import com.mcpscanner.scan.ScanTarget;
import com.mcpscanner.scan.request.ClientRegistrationRequestFactory;
import com.mcpscanner.scan.request.RequestGenerator;
import com.mcpscanner.scan.request.ScanMessage;
import com.mcpscanner.scan.request.ScanRequestRenderer;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public final class MiningConnection implements BaseRequestPlanner {

    private static final long FIRST_REQUEST_ID = 5000;

    private final AtomicLong requestIds = new AtomicLong(FIRST_REQUEST_ID);
    private final JsonRpcBaseRequests requests = new JsonRpcBaseRequests(requestIds::incrementAndGet);
    private final RequestGenerator generator = RequestGenerator.standard();
    private volatile ConnectResult inventory;
    private volatile String endpoint;
    private volatile Map<String, String> sessionHeaders = Map.of();
    private volatile McpOperationFraming.Framer framer;

    @Override
    public List<HttpRequest> plan(ConnectResult discovered, List<SeedRequest> seeds, String resolvedEndpoint,
                                  Map<String, String> headers, McpOperationFraming.Framer sessionFramer) {
        inventory = discovered;
        endpoint = resolvedEndpoint;
        sessionHeaders = Map.copyOf(headers);
        framer = sessionFramer;
        return seeds.stream().map(seed -> requests.build(endpoint, seed.routing().headers(sessionHeaders),
                new JsonRpcCall(seed.method(), seed.params()))).toList();
    }

    public ScanInventory inventory() {
        return new ScanInventory(inventory.tools(), inventory.resources(), inventory.resourceTemplates(),
                inventory.prompts());
    }

    public HttpRequest requestFor(SeedRequest seed) {
        return requests.build(endpoint, sessionHeaders, new JsonRpcCall(seed.method(), seed.params()));
    }

    public HttpRequest requestFor(ScanTarget target) {
        return requestFor(generator.scanThisRequest(target.family(), target.inventory()).messages().get(0));
    }

    public HttpRequest requestFor(ScanMessage message) {
        return message.renderWith(renderer(), endpoint, sessionHeaders);
    }

    private ScanRequestRenderer renderer() {
        return new ScanRequestRenderer(
                new HarnessRequestRenderer(new McpHttpRequestFactory(requestIds::incrementAndGet, framer)),
                new ClientRegistrationRequestFactory());
    }
}
