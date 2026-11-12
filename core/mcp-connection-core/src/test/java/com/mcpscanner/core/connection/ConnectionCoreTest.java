package com.mcpscanner.core.connection;

import java.util.Map;
import com.mcpscanner.core.protocol.McpFlow;

public final class ConnectionCoreTest {
    public static void main(String[] args) {
        equal(Map.of("Authorization", "Bearer fixture-token"), CredentialProviders.bearer("fixture-token").headers());
        var connection = new McpConnection(new McpFlow(8192, 10), CredentialProviders.bearer("fixture-token"));
        var session = connection.openHttp(request -> {
            equal("Bearer fixture-token", request.headers().get("Authorization"));
            return new McpFlow.HttpResponse(200, Map.of(), "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"capabilities\":{},\"supportedVersions\":[\"2026-07-28\"]}}", true);
        }, "fixture", "1");
        equal("2026-07-28", session.version());
        sseNotificationNeverWaitsForReply();
        anonymousSseHasSeparateStream();
        rejectUnsafeCredentials();
        retryIsBoundedAndAnonymousNeverRecovers();
        oauthRefreshAndScopeApproval();
        oauthRecoveryPreservesGrantMode();
        oauthRecoveryRejectsChangedCredentialGrantMode();
        anonymousRemovesExistingCredentialHeaders();
        sseGetRecoveryIsBounded();
        approvedScopeExpansionUsesSharedPolicy();
        ssePostRefreshReopensGetAndInitializes();
        scopeExpansionPreservesGrantedAndPreviouslyApprovedScopes();
        unchallengedDiscoveryRefusalFallsBackToInitialize();
        bearerChallengedDiscoveryStillRecovers();
        System.out.println("ConnectionCoreTest passed");
    }

    private static void sseNotificationNeverWaitsForReply() {
        var opened = new java.util.concurrent.atomic.AtomicInteger();
        var notifications = new java.util.concurrent.atomic.AtomicInteger();
        var recoveries = new java.util.concurrent.atomic.AtomicInteger();
        var provider = new CredentialProvider() {
            public Map<String, String> headers() { return Map.of("Authorization", "Bearer token-" + recoveries.get()); }
            public OperationRecovery beginOperation(String operation) {
                return response -> { recoveries.incrementAndGet(); return RecoveryResult.RETRY; };
            }
        };
        SseStreamFactory factory = credentials -> {
            opened.incrementAndGet();
            var events = new java.util.ArrayDeque<String>();
            events.add("event: endpoint\ndata: /messages\n\n");
            events.add("event: message\ndata: {\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"protocolVersion\":\"2024-11-05\"}}\n\n");
            return new SseStreamFactory.Stream() {
                public String nextEvent() {
                    if (events.isEmpty()) throw new AssertionError("Notification waited for JSON-RPC reply");
                    return events.remove();
                }
                public McpFlow.HttpResponse post(String endpoint, com.mcpscanner.core.protocol.McpRequests.Request request) {
                    equal(credentials.get("Authorization"), request.headers().get("Authorization"));
                    var document = (Map<?, ?>) com.mcpscanner.core.protocol.JsonDocuments.parse(request.body(), 65536);
                    if ("notifications/cancelled".equals(document.get("method"))) {
                        equal(false, document.containsKey("id"));
                        equal("2024-11-05", request.headers().get("MCP-Protocol-Version"));
                        if (notifications.incrementAndGet() == 1) return new McpFlow.HttpResponse(401, Map.of(), "denied", true);
                        return new McpFlow.HttpResponse(204, Map.of(), "", true);
                    }
                    return new McpFlow.HttpResponse(202, Map.of(), "", true);
                }
                public void close() {}
            };
        };
        try (var session = new McpConnection(new McpFlow(8192, 10), provider).openSse(factory, "https://fixture.example/sse", "fixture", "1")) {
            var response = session.sendNotification(session.session().notification("notifications/cancelled", "{}"));
            equal(204, response.status());
            equal(1, recoveries.get());
            equal(2, notifications.get());
            equal(2, opened.get());
        }
    }

    private static void anonymousSseHasSeparateStream() {
        var openedHeaders = new java.util.ArrayList<Map<String, String>>();
        var postedHeaders = new java.util.ArrayList<Map<String, String>>();
        SseStreamFactory factory = headers -> {
            openedHeaders.add(headers);
            var events = new java.util.ArrayDeque<String>();
            events.add("event: endpoint\ndata: /message\n\n");
            events.add("event: message\ndata: {\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"protocolVersion\":\"2024-11-05\"}}\n\n");
            return new SseStreamFactory.Stream() {
                public String nextEvent() { return events.poll(); }
                public McpFlow.HttpResponse post(String endpoint, com.mcpscanner.core.protocol.McpRequests.Request request) {
                    postedHeaders.add(request.headers());
                    return new McpFlow.HttpResponse(202, Map.of(), "", true);
                }
                public void close() {}
            };
        };
        var connection = new McpConnection(new McpFlow(8192, 10), CredentialProviders.bearer("secret"));
        try (var authenticated = connection.openSse(factory, "https://fixture.example/sse", "fixture", "1")) {
            equal("2024-11-05", authenticated.session().version());
        }
        try (var anonymous = connection.anonymous().openSse(factory, "https://fixture.example/sse", "fixture", "1")) {
            equal("2024-11-05", anonymous.session().version());
        }
        equal(2, openedHeaders.size());
        equal("Bearer secret", openedHeaders.get(0).get("Authorization"));
        equal(false, openedHeaders.get(1).containsKey("Authorization"));
        equal("Bearer secret", postedHeaders.get(0).get("Authorization"));
        equal(false, postedHeaders.get(2).containsKey("Authorization"));
        equal(false, postedHeaders.get(3).containsKey("Authorization"));
    }

    private static void rejectUnsafeCredentials() {
        expectFailure(() -> CredentialProviders.bearer("token\r\nX-Injected: yes"));
        expectFailure(() -> CredentialProviders.bearer(""));
        expectFailure(() -> CredentialProviders.custom(Map.of("MCP-Protocol-Version", "invalid")));
        expectFailure(() -> CredentialProviders.custom(Map.of("Host", "other.example")));
        var registry = new CredentialProviderRegistry().with("custom", CredentialProviders.custom(Map.of("X-Api-Key", "fixture")));
        equal(Map.of(), registry.resolve("none").headers());
        equal("fixture", registry.resolve("custom").headers().get("X-Api-Key"));
        expectFailure(() -> registry.resolve("unknown"));
    }

    private static void expectFailure(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Expected rejection");
    }

    private static void retryIsBoundedAndAnonymousNeverRecovers() {
        var recoveries = new java.util.concurrent.atomic.AtomicInteger();
        var sends = new java.util.concurrent.atomic.AtomicInteger();
        var credentials = new CredentialProvider() {
            public Map<String, String> headers() { return Map.of("Authorization", "Bearer fixture"); }
            public OperationRecovery beginOperation(String operation) {
                return response -> { recoveries.incrementAndGet(); return RecoveryResult.RETRY; };
            }
        };
        var connection = new McpConnection(new McpFlow(8192, 10), credentials);
        McpFlow.HttpPort denied = request -> {
            sends.incrementAndGet();
            return new McpFlow.HttpResponse(401, Map.of(), "", true);
        };
        var request = com.mcpscanner.core.protocol.McpRequests.modern("tools/list", "fixture", "1", null);
        equal(401, connection.send(denied, request).status());
        equal(3, sends.get());
        equal(2, recoveries.get());
        equal(401, connection.anonymous().send(denied, request).status());
        equal(4, sends.get());
        equal(2, recoveries.get());
    }

    private static void oauthRefreshAndScopeApproval() {
        var binding = new com.mcpscanner.core.oauth.CredentialBinding(java.net.URI.create("https://issuer.example"),
                java.net.URI.create("https://fixture.example/mcp"), "fixture-client", java.util.Set.of("read"));
        var refreshes = new java.util.concurrent.atomic.AtomicInteger();
        var authorizations = new java.util.concurrent.atomic.AtomicInteger();
        var current = new java.util.concurrent.atomic.AtomicReference<>(new com.mcpscanner.core.oauth.OAuthCredential(
                binding, "old-token", "refresh-token", java.time.Instant.MAX, java.util.Set.of("read")));
        var access = new OAuthCredentialAccess() {
            public java.util.Optional<com.mcpscanner.core.oauth.OAuthCredential> current() { return java.util.Optional.of(current.get()); }
            public boolean refreshAvailable() { return true; }
            public void refresh() {
                refreshes.incrementAndGet();
                current.set(new com.mcpscanner.core.oauth.OAuthCredential(binding, "new-token", "rotated", java.time.Instant.MAX, java.util.Set.of("read")));
            }
            public void authorize(java.util.Set<String> scopes) { authorizations.incrementAndGet(); }
        };
        var approvalRequests = new java.util.concurrent.atomic.AtomicInteger();
        var provider = new OAuthCredentialProvider(binding, access, () -> {
            approvalRequests.incrementAndGet();
            return com.mcpscanner.core.oauth.AuthorizationApproval.REQUIRE_PROMPT;
        });
        var connection = new McpConnection(new McpFlow(8192, 10), provider);
        var request = com.mcpscanner.core.protocol.McpRequests.modern("tools/list", "fixture", "1", null);
        var count = new java.util.concurrent.atomic.AtomicInteger();
        equal(200, connection.send(sent -> {
            if (count.getAndIncrement() == 0) return new McpFlow.HttpResponse(401, Map.of(), "", true);
            equal("Bearer new-token", sent.headers().get("Authorization"));
            return new McpFlow.HttpResponse(200, Map.of(), "{}", true);
        }, request).status());
        equal(1, refreshes.get());
        equal(0, approvalRequests.get());
        try {
            connection.send(sent -> new McpFlow.HttpResponse(403,
                Map.of("WWW-Authenticate", "Bearer error=\"insufficient_scope\", scope=\"read write\""), "", true), request);
            throw new AssertionError("Scope expansion needs approval");
        } catch (ConnectionAuthException expected) { equal(RecoveryResult.APPROVAL_REQUIRED, expected.result()); }
        equal(0, authorizations.get());
        equal(1, refreshes.get());
        equal(401, connection.anonymous().send(sent -> {
            equal(false, sent.headers().containsKey("Authorization"));
            return new McpFlow.HttpResponse(401, Map.of(), "", true);
        }, request).status());
        equal(1, refreshes.get());
    }

    private static void oauthRecoveryPreservesGrantMode() {
        for (var mode : com.mcpscanner.core.oauth.OAuthGrantMode.values()) {
            var binding = new com.mcpscanner.core.oauth.CredentialBinding(java.net.URI.create("https://issuer.example"),
                    java.net.URI.create("https://fixture.example/mcp"), "fixture-client", java.util.Set.of("read"), mode);
            var refreshes = new java.util.concurrent.atomic.AtomicInteger();
            var access = new OAuthCredentialAccess() {
                public java.util.Optional<com.mcpscanner.core.oauth.OAuthCredential> current() {
                    return java.util.Optional.of(new com.mcpscanner.core.oauth.OAuthCredential(binding,
                            "token-" + refreshes.get(), "refresh", java.time.Instant.MAX, java.util.Set.of("read")));
                }
                public boolean refreshAvailable() { return true; }
                public void refresh() { refreshes.incrementAndGet(); }
                public void authorize(java.util.Set<String> scopes) { throw new AssertionError("Unexpected authorization"); }
            };
            var connection = new McpConnection(new McpFlow(8192, 10), new OAuthCredentialProvider(binding, access,
                    () -> com.mcpscanner.core.oauth.AuthorizationApproval.REQUIRE_PROMPT));
            var sends = new java.util.concurrent.atomic.AtomicInteger();
            var request = com.mcpscanner.core.protocol.McpRequests.modern("tools/list", "fixture", "1", null);
            equal(200, connection.send(sent -> {
                sends.incrementAndGet();
                if (sent.headers().get("Authorization").equals("Bearer token-0"))
                    return new McpFlow.HttpResponse(401, Map.of(), "", true);
                return new McpFlow.HttpResponse(200, Map.of(), "{}", true);
            }, request).status());
            equal(1, refreshes.get());
            equal(2, sends.get());
        }
    }

    private static void oauthRecoveryRejectsChangedCredentialGrantMode() {
        var binding = new com.mcpscanner.core.oauth.CredentialBinding(java.net.URI.create("https://issuer.example"),
                java.net.URI.create("https://fixture.example/mcp"), "fixture-client", java.util.Set.of("read"));
        var currentBinding = new java.util.concurrent.atomic.AtomicReference<>(binding);
        var access = new OAuthCredentialAccess() {
            public java.util.Optional<com.mcpscanner.core.oauth.OAuthCredential> current() {
                return java.util.Optional.of(new com.mcpscanner.core.oauth.OAuthCredential(currentBinding.get(),
                        "token", "refresh", java.time.Instant.MAX, java.util.Set.of("read")));
            }
            public boolean refreshAvailable() { return true; }
            public void refresh() { throw new AssertionError("Mismatched credential must not refresh"); }
            public void authorize(java.util.Set<String> scopes) { throw new AssertionError("Mismatched credential must not authorize"); }
        };
        var provider = new OAuthCredentialProvider(binding, access,
                () -> com.mcpscanner.core.oauth.AuthorizationApproval.APPROVED);
        var recovery = provider.beginOperation("tools/list");
        currentBinding.set(new com.mcpscanner.core.oauth.CredentialBinding(binding.issuer(), binding.resource(),
                binding.clientId(), binding.scopes(), com.mcpscanner.core.oauth.OAuthGrantMode.CLIENT_CREDENTIALS));
        try {
            recovery.recover(new McpFlow.HttpResponse(401, Map.of(), "", true));
            throw new AssertionError("Changed credential grant mode must be rejected");
        } catch (com.mcpscanner.core.oauth.OAuthPolicyException expected) { }
    }

    private static void anonymousRemovesExistingCredentialHeaders() {
        var connection = new McpConnection(new McpFlow(8192, 10), CredentialProviders.custom(Map.of("X-Api-Key", "fixture")));
        var request = new com.mcpscanner.core.protocol.McpRequests.Request("{}",
                Map.of("x-api-key", "old", "authorization", "Bearer old", "Cookie", "session=old", "MCP-Protocol-Version", "2026-07-28"));
        connection.anonymous().send(sent -> {
            equal(Map.of("MCP-Protocol-Version", "2026-07-28"), sent.headers());
            return new McpFlow.HttpResponse(200, Map.of(), "", true);
        }, request);
    }

    private static void sseGetRecoveryIsBounded() {
        var opens = new java.util.concurrent.atomic.AtomicInteger();
        var recoveries = new java.util.concurrent.atomic.AtomicInteger();
        var credentials = new CredentialProvider() {
            public Map<String, String> headers() { return Map.of("Authorization", "Bearer fixture"); }
            public OperationRecovery beginOperation(String operation) {
                return response -> { recoveries.incrementAndGet(); return RecoveryResult.RETRY; };
            }
        };
        var connection = new McpConnection(new McpFlow(8192, 10), credentials);
        SseStreamFactory denied = headers -> {
            opens.incrementAndGet();
            throw new SseOpenException(new McpFlow.HttpResponse(401, Map.of(), "", true));
        };
        try { connection.openSse(denied, "https://fixture.example/sse", "fixture", "1"); }
        catch (SseOpenException expected) { equal(401, expected.response().status()); }
        equal(3, opens.get());
        equal(2, recoveries.get());
        try { connection.anonymous().openSse(denied, "https://fixture.example/sse", "fixture", "1"); }
        catch (SseOpenException expected) { equal(401, expected.response().status()); }
        equal(4, opens.get());
        equal(2, recoveries.get());
    }

    private static void approvedScopeExpansionUsesSharedPolicy() {
        var binding = new com.mcpscanner.core.oauth.CredentialBinding(java.net.URI.create("https://issuer.example"),
                java.net.URI.create("https://fixture.example/mcp"), "fixture-client", java.util.Set.of("read"));
        var authorizations = new java.util.concurrent.atomic.AtomicInteger();
        var access = new OAuthCredentialAccess() {
            public java.util.Optional<com.mcpscanner.core.oauth.OAuthCredential> current() {
                return java.util.Optional.of(new com.mcpscanner.core.oauth.OAuthCredential(binding, "token", null, java.time.Instant.MAX, java.util.Set.of("read")));
            }
            public boolean refreshAvailable() { return false; }
            public void refresh() { throw new AssertionError("No refresh"); }
            public void authorize(java.util.Set<String> scopes) {
                equal(java.util.Set.of("read", "write"), scopes);
                authorizations.incrementAndGet();
            }
        };
        var provider = new OAuthCredentialProvider(binding, access, scopes -> {
            equal(java.util.Set.of("read", "write"), scopes);
            return com.mcpscanner.core.oauth.AuthorizationApproval.APPROVED;
        });
        var connection = new McpConnection(new McpFlow(8192, 10), provider);
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        equal(200, connection.send(request -> calls.getAndIncrement() == 0
                ? new McpFlow.HttpResponse(403, Map.of("WWW-Authenticate", "Bearer error=\"insufficient_scope\", scope=\"write\""), "", true)
                : new McpFlow.HttpResponse(200, Map.of(), "{}", true),
                com.mcpscanner.core.protocol.McpRequests.modern("tools/list", "fixture", "1", null)).status());
        equal(1, authorizations.get());
    }

    private static void ssePostRefreshReopensGetAndInitializes() {
        var token = new java.util.concurrent.atomic.AtomicReference<>("old");
        var refreshes = new java.util.concurrent.atomic.AtomicInteger();
        var opens = new java.util.ArrayList<Map<String, String>>();
        var handshakes = new java.util.concurrent.atomic.AtomicInteger();
        var closed = new java.util.concurrent.atomic.AtomicInteger();
        var credentials = new CredentialProvider() {
            public Map<String, String> headers() { return Map.of("Authorization", "Bearer " + token.get()); }
            public OperationRecovery beginOperation(String operation) {
                return response -> { refreshes.incrementAndGet(); token.set("new"); return RecoveryResult.RETRY; };
            }
        };
        SseStreamFactory factory = headers -> {
            opens.add(headers);
            String streamToken = headers.get("Authorization");
            var events = new java.util.ArrayDeque<>(java.util.List.of("event: endpoint\ndata: /message\n\n",
                "event: message\ndata: {\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"protocolVersion\":\"2024-11-05\"}}\n\n",
                "event: message\ndata: {\"jsonrpc\":\"2.0\",\"id\":9,\"result\":{}}\n\n"));
            return new SseStreamFactory.Stream() {
                public String nextEvent() { return events.poll(); }
                public McpFlow.HttpResponse post(String endpoint, com.mcpscanner.core.protocol.McpRequests.Request request) {
                    equal(streamToken, request.headers().get("Authorization"));
                    if (request.body().contains("\"initialize\"")) handshakes.incrementAndGet();
                    if (request.body().contains("tools/call") && streamToken.equals("Bearer old"))
                        return new McpFlow.HttpResponse(401, Map.of(), "", true);
                    return new McpFlow.HttpResponse(202, Map.of(), "", true);
                }
                public void close() { closed.incrementAndGet(); }
            };
        };
        var connection = new McpConnection(new McpFlow(8192, 10), credentials);
        try (var session = connection.openSse(factory, "https://fixture.example/sse", "fixture", "1")) {
            equal(9L, session.call(session.session().operation("tools/call", "{\"name\":\"fixture\",\"arguments\":{}}", 9), 9).id());
            equal(2, opens.size());
            equal("Bearer new", opens.get(1).get("Authorization"));
            equal(2, handshakes.get());
            equal(1, refreshes.get());
        }
        equal(2, closed.get());
    }

    private static void scopeExpansionPreservesGrantedAndPreviouslyApprovedScopes() {
        var binding = new com.mcpscanner.core.oauth.CredentialBinding(java.net.URI.create("https://issuer.example"),
                java.net.URI.create("https://fixture.example/mcp"), "fixture-client", java.util.Set.of("read"));
        var authorized = new java.util.ArrayList<java.util.Set<String>>();
        var access = new OAuthCredentialAccess() {
            public java.util.Optional<com.mcpscanner.core.oauth.OAuthCredential> current() {
                return java.util.Optional.of(new com.mcpscanner.core.oauth.OAuthCredential(
                    binding, "token", null, java.time.Instant.MAX, java.util.Set.of("read", "delete")));
            }
            public boolean refreshAvailable() { return false; }
            public void refresh() { throw new AssertionError(); }
            public void authorize(java.util.Set<String> scopes) { authorized.add(scopes); }
        };
        var provider = new OAuthCredentialProvider(binding, access, scopes -> com.mcpscanner.core.oauth.AuthorizationApproval.APPROVED);
        var connection = new McpConnection(new McpFlow(8192, 10), provider);
        var request = com.mcpscanner.core.protocol.McpRequests.modern("tools/list", "fixture", "1", null);
        for (String scope : java.util.List.of("write", "admin")) {
            var calls = new java.util.concurrent.atomic.AtomicInteger();
            connection.send(sent -> calls.getAndIncrement() == 0
                ? new McpFlow.HttpResponse(403, Map.of("WWW-Authenticate", "Bearer error=\"insufficient_scope\", scope=\"" + scope + "\""), "", true)
                : new McpFlow.HttpResponse(200, Map.of(), "{}", true), request);
        }
        equal(java.util.Set.of("read", "delete", "write"), authorized.get(0));
        equal(java.util.Set.of("read", "delete", "write", "admin"), authorized.get(1));
    }

    private static void unchallengedDiscoveryRefusalFallsBackToInitialize() {
        for (var refusal : java.util.List.of(
                new McpFlow.HttpResponse(403, Map.of(), "Forbidden", true),
                new McpFlow.HttpResponse(401, Map.of(), "", true),
                new McpFlow.HttpResponse(401, Map.of("WWW-Authenticate", "Basic realm=\"gateway\""), "", true),
                new McpFlow.HttpResponse(403, Map.of("WWW-Authenticate", "Bearer error=unparseable description"), "", true))) {
            var recoveries = new java.util.concurrent.atomic.AtomicInteger();
            var session = recordingConnection(recoveries).openHttp(request -> {
                if (request.body().contains("server/discover")) return refusal;
                if (request.body().contains("\"initialize\""))
                    return new McpFlow.HttpResponse(200, Map.of("Mcp-Session-Id", "legacy"),
                        "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"protocolVersion\":\"2025-11-25\"}}", true);
                return new McpFlow.HttpResponse(202, Map.of(), "", true);
            }, "fixture", "1");
            equal("2025-11-25", session.version());
            equal("legacy", session.sessionId());
            equal(0, recoveries.get());
        }
    }

    private static void bearerChallengedDiscoveryStillRecovers() {
        for (var challenge : java.util.List.of(
                new McpFlow.HttpResponse(401, Map.of("WWW-Authenticate", "Bearer error=\"invalid_token\""), "", true),
                new McpFlow.HttpResponse(403, Map.of("WWW-Authenticate",
                    "Bearer error=\"insufficient_scope\", scope=\"write\""), "", true),
                new McpFlow.HttpResponse(403, Map.of("WWW-Authenticate",
                    "Basic realm=\"fixture\", Bearer resource_metadata=\"https://fixture.example/.well-known/oauth-protected-resource\""), "", true))) {
            var recoveries = new java.util.concurrent.atomic.AtomicInteger();
            var session = recordingConnection(recoveries).openHttp(request -> {
                if (recoveries.get() == 0) return challenge;
                return new McpFlow.HttpResponse(200, Map.of(),
                    "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"capabilities\":{},\"supportedVersions\":[\"2026-07-28\"]}}", true);
            }, "fixture", "1");
            equal("2026-07-28", session.version());
            equal(1, recoveries.get());
        }
    }

    private static McpConnection recordingConnection(java.util.concurrent.atomic.AtomicInteger recoveries) {
        return new McpConnection(new McpFlow(8192, 10), new CredentialProvider() {
            public Map<String, String> headers() { return Map.of("Authorization", "Bearer fixture"); }
            public OperationRecovery beginOperation(String operation) {
                return response -> { recoveries.incrementAndGet(); return RecoveryResult.RETRY; };
            }
        });
    }

    private static void equal(Object expected, Object actual) {
        if (!expected.equals(actual)) throw new AssertionError("Expected " + expected + " but was " + actual);
    }
}
