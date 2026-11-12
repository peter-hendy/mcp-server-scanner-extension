package com.mcpscanner.core.connection;

import com.mcpscanner.core.protocol.ClientProfile;
import com.mcpscanner.core.protocol.McpFlow;
import com.mcpscanner.core.protocol.McpRequests;
import java.util.LinkedHashMap;
import java.util.Map;

public final class McpConnection {
    private final McpFlow flow;
    private final CredentialProvider credentials;
    private final java.util.Set<String> credentialHeaderNames;

    public McpConnection(McpFlow flow, CredentialProvider credentials) {
        this(flow, credentials, credentialNames(credentials));
    }

    private McpConnection(McpFlow flow, CredentialProvider credentials, java.util.Set<String> credentialHeaderNames) {
        this.flow = java.util.Objects.requireNonNull(flow);
        this.credentials = java.util.Objects.requireNonNull(credentials);
        this.credentialHeaderNames = credentialHeaderNames;
    }

    public HttpSession openHttpSession(McpFlow.HttpPort port, String name, String version) {
        return openHttpSession(port, ClientProfile.of(name, version));
    }

    public HttpSession openHttpSession(McpFlow.HttpPort port, ClientProfile client) {
        return new HttpSession(this, flow, port, openHttp(port, client));
    }

    public HttpSession openInitializeHttpSession(McpFlow.HttpPort port, String name, String version, String preferredVersion) {
        return openInitializeHttpSession(port, ClientProfile.of(name, version), preferredVersion);
    }

    public HttpSession openInitializeHttpSession(McpFlow.HttpPort port, ClientProfile client, String preferredVersion) {
        return new HttpSession(this, flow, port, initialize(port, client, preferredVersion));
    }

    public HttpProbeSession openInitializeHttpProbe(McpFlow.HttpPort port, SessionDeletePort deletePort,
                                                    String name, String version, String preferredVersion) {
        return openInitializeHttpProbe(port, deletePort, ClientProfile.of(name, version), preferredVersion);
    }

    public HttpProbeSession openInitializeHttpProbe(McpFlow.HttpPort port, SessionDeletePort deletePort,
                                                    ClientProfile client, String preferredVersion) {
        var handshake = new IssuedSessionRecorder(port, preferredVersion);
        try {
            var session = new HttpSession(this, flow, port, initialize(handshake, client, preferredVersion),
                SessionExpiry.REPORT);
            return new HttpProbeSession(this, session, deletePort);
        } catch (RuntimeException failure) {
            handshake.issuedSessionId().ifPresent(sessionId ->
                terminateAfterFailure(deletePort, sessionId, handshake.version(), failure));
            throw failure;
        }
    }

    McpFlow.HttpResponse terminate(SessionDeletePort deletePort, String sessionId, String version) {
        var termination = new McpRequests.Request("", Map.of("MCP-Session-Id", sessionId, "MCP-Protocol-Version", version));
        return send(request -> deletePort.delete(request.headers()), termination);
    }

    private void terminateAfterFailure(SessionDeletePort deletePort, String sessionId, String version,
                                       RuntimeException failure) {
        try {
            terminate(deletePort, sessionId, version);
        } catch (RuntimeException cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }

    private McpFlow.Session initialize(McpFlow.HttpPort port, ClientProfile client, String preferredVersion) {
        return flow.openInitializeHttp(request -> admission(send(port, request)), client, preferredVersion);
    }

    public McpFlow.Session openHttp(McpFlow.HttpPort port, String name, String version) {
        return openHttp(port, ClientProfile.of(name, version));
    }

    public McpFlow.Session openHttp(McpFlow.HttpPort port, ClientProfile client) {
        return flow.openHttp(request -> discoveryProbe(request) ? discover(port, request) : admission(send(port, request)), client);
    }

    public McpConnection anonymous() { return new McpConnection(flow, CredentialProviders.none(), credentialHeaderNames); }

    public McpFlow.HttpResponse send(McpFlow.HttpPort port, McpRequests.Request request) {
        return send(port, request, this::challenged);
    }

    private McpFlow.HttpResponse send(McpFlow.HttpPort port, McpRequests.Request request,
            java.util.function.Predicate<McpFlow.HttpResponse> recoverable) {
        var recovery = beginRecovery();
        for (int attempt = 0; ; attempt++) {
            var response = port.send(authenticate(request));
            if (!recoverable.test(response) || attempt == 2 || !recover(recovery, response)) return response;
        }
    }

    private McpFlow.HttpResponse discover(McpFlow.HttpPort port, McpRequests.Request request) {
        var response = send(port, request, BearerChallenges::genuine);
        if (BearerChallenges.genuine(response)) throw new ConnectionAuthException(RecoveryResult.STOP);
        return response;
    }

    private static boolean discoveryProbe(McpRequests.Request request) {
        return Headers.first(request.headers(), "Mcp-Method").filter("server/discover"::equals).isPresent();
    }

    boolean challenged(McpFlow.HttpResponse response) {
        return response != null && (response.status() == 401 || response.status() == 403);
    }

    boolean recover(OperationRecovery recovery, McpFlow.HttpResponse response) {
        var result = recovery.recover(response);
        if (result == RecoveryResult.APPROVAL_REQUIRED || result == RecoveryResult.DENIED)
            throw new ConnectionAuthException(result);
        return result == RecoveryResult.RETRY;
    }

    private McpFlow.HttpResponse admission(McpFlow.HttpResponse response) {
        if (challenged(response)) throw new ConnectionAuthException(RecoveryResult.STOP);
        return response;
    }

    public SseSession openSse(SseStreamFactory factory, String origin, String name, String version) {
        return openSse(factory, origin, ClientProfile.of(name, version));
    }

    public SseSession openSse(SseStreamFactory factory, String origin, ClientProfile client) {
        return new SseSession(flow, this, factory, origin, client, SessionExpiry.REINITIALIZE);
    }

    public SseProbeSession openSseProbe(SseStreamFactory factory, String origin, String name, String version) {
        return openSseProbe(factory, origin, ClientProfile.of(name, version));
    }

    public SseProbeSession openSseProbe(SseStreamFactory factory, String origin, ClientProfile client) {
        return new SseProbeSession(new SseSession(flow, this, factory, origin, client, SessionExpiry.REPORT));
    }

    McpRequests.Request authenticate(McpRequests.Request request) {
        return attach(request, credentialHeaders());
    }

    McpRequests.Request attach(McpRequests.Request request, Map<String, String> credentialHeaders) {
        Map<String, String> headers = new LinkedHashMap<>(request.headers());
        headers.keySet().removeIf(name -> credentialHeaderNames.contains(name.toLowerCase(java.util.Locale.ROOT)));
        headers.putAll(credentialHeaders);
        return new McpRequests.Request(request.body(), Map.copyOf(headers));
    }

    Map<String, String> credentialHeaders() { return new HeaderCredentials(credentials.headers()).headers(); }
    OperationRecovery beginRecovery() { return credentials.beginOperation(java.util.UUID.randomUUID().toString()); }

    private static java.util.Set<String> credentialNames(CredentialProvider credentials) {
        var names = new java.util.HashSet<>(java.util.Set.of("authorization", "cookie", "proxy-authorization"));
        credentials.headerNames().forEach(name -> names.add(name.toLowerCase(java.util.Locale.ROOT)));
        return java.util.Set.copyOf(names);
    }
}
