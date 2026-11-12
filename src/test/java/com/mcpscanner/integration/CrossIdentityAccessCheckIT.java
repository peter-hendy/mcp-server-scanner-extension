package com.mcpscanner.integration;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.scanner.audit.issues.AuditIssue;
import burp.api.montoya.scanner.audit.issues.AuditIssueConfidence;
import com.mcpscanner.auth.BearerTokenAuthStrategy;
import com.mcpscanner.checks.api.AccessExpectation;
import com.mcpscanner.checks.api.AccessItemKey;
import com.mcpscanner.checks.api.AccessLedger;
import com.mcpscanner.checks.api.AccessOutcome;
import com.mcpscanner.access.AccessComparisonRun;
import com.mcpscanner.access.comparison.CrossIdentityComparison;
import com.mcpscanner.access.comparison.CrossIdentityPorts;
import com.mcpscanner.checks.api.CheckCatalog;
import com.mcpscanner.checks.api.CheckRunner;
import com.mcpscanner.checks.api.CheckServices;
import com.mcpscanner.checks.api.McpCheck;
import com.mcpscanner.checks.api.Principal;
import com.mcpscanner.checks.api.PrincipalListings;
import com.mcpscanner.checks.api.PrincipalRole;
import com.mcpscanner.checks.api.PrincipalRoster;
import com.mcpscanner.client.TransportType;
import com.mcpscanner.cvetargets.requests.VerbatimHeaderHttp;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.scan.McpInsertionPointProvider;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "MCP_E2E_IT", matches = "1")
class CrossIdentityAccessCheckIT {

    private static final String SESSION_CHECK_ID = "session-binding";
    private static final String OPERATION = "MCP operation available to a lower-privilege identity";
    private static final String RESULT = "MCP operation result available to another principal";
    private static final String NOTIFICATION = "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}";
    private static final List<String> MODULES = List.of("cross_identity_object", "cross_identity_resource",
            "cross_identity_role", "cross_identity_session");

    private static final Seed DOCUMENT = Seed.tool("get_document", "{\"doc_id\":\"d-alice-0001\"}",
            AccessExpectation.ANY_SIGNED_IN, true);
    private static final Seed PRIVATE_NOTE = Seed.resource("notes://alice/private.md", AccessExpectation.OWNER_ONLY, false);
    private static final Seed DELETE_PROJECT = Seed.tool("delete_project", "{\"project_id\":\"p-1\"}",
            AccessExpectation.NOT_LOWER, false);
    private static final Seed LIST_USERS = Seed.tool("list_users", "{}", AccessExpectation.OWNER_ONLY, false);
    private static final Seed WHOAMI = Seed.tool("whoami", "{}", AccessExpectation.ANY_SIGNED_IN, true);
    private static final Seed SEARCH = Seed.tool("search_docs", "{\"query\":\"handbook\"}",
            AccessExpectation.ANY_SIGNED_IN, true);
    private static final Seed HANDBOOK = Seed.resource("notes://team/handbook.md", AccessExpectation.SHARED_BY_DESIGN, true);
    private static final List<Seed> STATELESS_SEEDS = List.of(DOCUMENT, DELETE_PROJECT, LIST_USERS, WHOAMI);
    private static final List<Seed> SEEDS = List.of(DOCUMENT, PRIVATE_NOTE, DELETE_PROJECT, LIST_USERS, WHOAMI, SEARCH,
            HANDBOOK);

    private final Http http = new VerbatimHeaderHttp();

    @BeforeEach
    void installMontoya() {
        MontoyaTestFactory.install();
    }

    @Test
    void vulnerableModulesFireTheirIssueFamilyAndControlsStaySilent() throws Exception {
        Map<Seed, List<AuditIssue>> issues = scanDirect(MODULES.stream().map(module -> module + "=vulnerable").toList());

        assertVulnerable(issues);
    }

    @Test
    void vulnerableModulesFireThroughTheScannerProxyFixture() throws Exception {
        Map<Seed, List<AuditIssue>> issues = scanThroughProxy(
                MODULES.stream().map(module -> module + "=vulnerable").toList());

        assertVulnerable(issues);
    }

    @Test
    void safeModulesAreSilent() throws Exception {
        Map<Seed, List<AuditIssue>> direct = scanDirect(MODULES.stream().map(module -> module + "=safe").toList());
        Map<Seed, List<AuditIssue>> proxied = scanThroughProxy(MODULES.stream().map(module -> module + "=safe").toList());

        assertThat(direct.values()).allSatisfy(found -> assertThat(found).isEmpty());
        assertThat(proxied.values()).allSatisfy(found -> assertThat(found).isEmpty());
    }

    @Test
    void sessionModuleFiresThroughTheSessionBindingCheckOnly() throws Exception {
        try (McpCheckItSupport.RunningServer server = startServer(List.of("cross_identity_session=vulnerable"))) {
            String endpoint = McpCheckItSupport.mcpEndpoint(server.port());
            Map<String, String> headers = Map.of("Authorization", "Bearer alice-token",
                    "Mcp-Session-Id", openSession(endpoint));

            Map<Seed, List<AuditIssue>> issues = scanWithCheck(SESSION_CHECK_ID, http, endpoint, headers,
                    List.of(WHOAMI));

            assertThat(issues.get(WHOAMI)).isNotEmpty()
                    .allSatisfy(issue -> assertThat(issue.detail()).contains("Probe: session."));
        }
    }

    @ParameterizedTest
    @EnumSource(value = TransportType.class, names = {"SSE", "MODERN_HTTP"})
    void objectAndRoleModulesFireOnStatelessTransportsAndSkipSessionProbes(TransportType transport) throws Exception {
        Scan vulnerable = scanThroughProxy(List.of("cross_identity_object=vulnerable", "cross_identity_role=vulnerable",
                "cross_identity_session=vulnerable"), transport, STATELESS_SEEDS);
        Scan safe = scanThroughProxy(List.of("cross_identity_object=safe", "cross_identity_role=safe",
                "cross_identity_session=safe"), transport, STATELESS_SEEDS);

        assertThat(vulnerable.issues().get(DOCUMENT)).anySatisfy(issue -> {
            assertThat(issue.name()).isEqualTo(RESULT);
            assertThat(issue.detail()).contains("Probe: private-marker.");
            assertThat(issue.confidence()).isEqualTo(AuditIssueConfidence.FIRM);
        }).anySatisfy(issue -> assertThat(issue.detail()).contains("Probe: foreign-reference."));
        assertThat(vulnerable.issues().get(DELETE_PROJECT)).singleElement().satisfies(issue -> {
            assertThat(issue.name()).isEqualTo(OPERATION);
            assertThat(issue.confidence()).isEqualTo(AuditIssueConfidence.FIRM);
            assertThat(issue.detail()).contains("Identity: carol");
        });
        assertThat(vulnerable.issues().get(LIST_USERS)).hasSize(2)
                .allSatisfy(issue -> assertThat(issue.name()).isEqualTo(OPERATION));
        assertThat(vulnerable.issues().get(WHOAMI)).isEmpty();
        assertThat(vulnerable.debugLines()).noneMatch(line -> line.contains("session_binding:")
                || line.contains("session_rebind"));
        assertThat(safe.issues().values()).allSatisfy(found -> assertThat(found).isEmpty());
    }

    private static void assertVulnerable(Map<Seed, List<AuditIssue>> issues) {
        assertThat(issues.get(DOCUMENT)).anySatisfy(issue -> {
            assertThat(issue.name()).isEqualTo(RESULT);
            assertThat(issue.detail()).contains("Probe: private-marker.");
            assertThat(issue.confidence()).isEqualTo(AuditIssueConfidence.FIRM);
        }).anySatisfy(issue -> assertThat(issue.detail()).contains("Probe: foreign-reference."));
        assertThat(issues.get(PRIVATE_NOTE)).hasSize(2).allSatisfy(issue -> {
            assertThat(issue.name()).isEqualTo(OPERATION);
            assertThat(issue.confidence()).isEqualTo(AuditIssueConfidence.FIRM);
        });
        assertThat(issues.get(DELETE_PROJECT)).singleElement().satisfies(issue -> {
            assertThat(issue.name()).isEqualTo(OPERATION);
            assertThat(issue.detail()).contains("Identity: carol");
        });
        assertThat(issues.get(LIST_USERS)).hasSize(2).allSatisfy(issue -> assertThat(issue.name()).isEqualTo(OPERATION));
        assertThat(issues.get(WHOAMI)).noneSatisfy(issue -> assertThat(issue.detail()).contains("Probe: session."));
        assertThat(issues.get(SEARCH)).isEmpty();
        assertThat(issues.get(HANDBOOK)).isEmpty();
    }

    private Map<Seed, List<AuditIssue>> scanDirect(List<String> vulns) throws Exception {
        try (McpCheckItSupport.RunningServer server = startServer(vulns)) {
            String endpoint = McpCheckItSupport.mcpEndpoint(server.port());
            Map<String, String> headers = Map.of("Authorization", "Bearer alice-token",
                    "Mcp-Session-Id", openSession(endpoint));
            return scanAll(http, endpoint, headers, SEEDS, McpEventLog.noop());
        }
    }

    private Map<Seed, List<AuditIssue>> scanThroughProxy(List<String> vulns) throws Exception {
        return scanThroughProxy(vulns, TransportType.STREAMABLE_HTTP, SEEDS).issues();
    }

    private Scan scanThroughProxy(List<String> vulns, TransportType transport, List<Seed> seeds) throws Exception {
        try (McpCheckItSupport.RunningServer server = startServer(vulns)) {
            String mcp = McpCheckItSupport.mcpEndpoint(server.port());
            String endpoint = transport == TransportType.SSE ? mcp.replace("/mcp", "/sse") : mcp;
            try (ScannerProxyFixture proxy = ScannerProxyFixture.connect(endpoint, transport)) {
                McpEventLog eventLog = new McpEventLog(null);
                Map<Seed, List<AuditIssue>> issues = scanAll(proxy.routed(http), proxy.session().resolvedEndpoint(),
                        proxy.baseHeaders(), seeds, eventLog);
                return new Scan(issues, eventLog.snapshot().stream()
                        .filter(entry -> entry.level() == McpEventLog.Level.DEBUG)
                        .map(McpEventLog.LogEntry::message).toList());
            }
        }
    }

    private record Scan(Map<Seed, List<AuditIssue>> issues, List<String> debugLines) {
    }

    private static McpCheckItSupport.RunningServer startServer(List<String> vulns) throws Exception {
        List<String> args = new java.util.ArrayList<>(List.of("--default-mode", "safe"));
        vulns.forEach(vuln -> args.addAll(List.of("--vuln", vuln)));
        return McpCheckItSupport.startServer(args.toArray(String[]::new));
    }

    private static Map<Seed, List<AuditIssue>> scanAll(Http http, String endpoint, Map<String, String> headers,
                                                       List<Seed> seeds, McpEventLog eventLog) {
        Map<Seed, List<AuditIssue>> issues = new LinkedHashMap<>();
        for (Seed seed : seeds) {
            Ledger ledger = new Ledger(seed);
            CrossIdentityComparison comparison = new CrossIdentityComparison(eventLog,
                    () -> new BearerTokenAuthStrategy("alice-token"), CrossIdentityAccessCheckIT::roster,
                    CrossIdentityPorts.of(ledger, ledger));
            HttpRequestResponse base = base(http, endpoint, headers, seed.body());
            try (AccessComparisonRun run = comparison.begin(http)) {
                issues.put(seed, run.compare(base));
            }
            assertThat(ledger.outcomes).as("outcomes recorded for " + seed.name())
                    .containsKeys("bob", "carol", "anonymous");
        }
        return issues;
    }

    private static Map<Seed, List<AuditIssue>> scanWithCheck(String checkId, Http http, String endpoint,
                                                             Map<String, String> headers, List<Seed> seeds) {
        Map<Seed, List<AuditIssue>> issues = new LinkedHashMap<>();
        for (Seed seed : seeds) {
            Ledger ledger = new Ledger(seed);
            McpCheck check = CheckCatalog.discover(new CheckServices(McpEventLog.noop(),
                            () -> new BearerTokenAuthStrategy("alice-token"), Optional::empty, Optional::empty,
                            CrossIdentityAccessCheckIT::roster, ledger, ledger))
                    .find(checkId).orElseThrow();
            HttpRequestResponse base = base(http, endpoint, headers, seed.body());
            issues.put(seed, new CheckRunner(http, new McpInsertionPointProvider()::provideInsertionPoints)
                    .run(check, List.of(base)));
        }
        return issues;
    }

    private static PrincipalRoster roster() {
        Principal alice = new Principal(Principal.PRIMARY_ID, "alice", PrincipalRole.PRIMARY,
                () -> Map.of("Authorization", "Bearer alice-token"), Optional.of("ALICE-PRIVATE-7f3c"), Map.of());
        Principal bob = new Principal("bob", "bob", PrincipalRole.PEER, () -> Map.of("Authorization", "Bearer bob-token"),
                Optional.of("BOB-PRIVATE-91ad"), Map.of("doc_id", "d-bob-0042"));
        Principal carol = new Principal("carol", "carol", PrincipalRole.LOWER,
                () -> Map.of("Authorization", "Bearer carol-token"), Optional.empty(), Map.of());
        return PrincipalRoster.of(alice, List.of(bob, carol));
    }

    private record Seed(String name, String body, AccessExpectation expectation, boolean listedToEveryone) {

        static Seed tool(String name, String arguments, AccessExpectation expectation, boolean listedToEveryone) {
            return new Seed(name, "{\"jsonrpc\":\"2.0\",\"id\":41,\"method\":\"tools/call\",\"params\":{\"name\":\""
                    + name + "\",\"arguments\":" + arguments + "}}", expectation, listedToEveryone);
        }

        static Seed resource(String uri, AccessExpectation expectation, boolean listedToEveryone) {
            return new Seed(uri, "{\"jsonrpc\":\"2.0\",\"id\":42,\"method\":\"resources/read\",\"params\":{\"uri\":\""
                    + uri + "\"}}", expectation, listedToEveryone);
        }
    }

    private static final class Ledger implements AccessLedger, PrincipalListings {

        private final Seed seed;
        private final Map<String, AccessOutcome> outcomes = new ConcurrentHashMap<>();

        Ledger(Seed seed) {
            this.seed = seed;
        }

        @Override
        public AccessExpectation expectationFor(AccessItemKey item) {
            return seed.expectation();
        }

        @Override
        public void record(AccessItemKey item, String principalId, AccessOutcome outcome) {
            outcomes.put(principalId, outcome);
        }

        @Override
        public void recordAnonymous(AccessItemKey item, AccessOutcome outcome) {
            outcomes.put("anonymous", outcome);
        }

        @Override
        public Optional<Boolean> lists(String principalId, AccessItemKey item) {
            return Optional.of(seed.listedToEveryone());
        }

        @Override
        public boolean permitsReplay(AccessItemKey item) {
            return true;
        }
    }


    private static HttpRequestResponse base(Http http, String endpoint, Map<String, String> headers, String body) {
        HttpRequest request = HttpRequest.httpRequestFromUrl(endpoint).withMethod("POST")
                .withHeader("Content-Type", "application/json")
                .withHeader("Accept", "application/json, text/event-stream");
        for (Map.Entry<String, String> header : headers.entrySet()) {
            request = request.withHeader(header.getKey(), header.getValue());
        }
        HttpRequestResponse base = http.sendRequest(request.withBody(body));
        assertThat(base.response().statusCode()).as("authenticated base request").isBetween((short) 200, (short) 299);
        return base;
    }

    private static String openSession(String endpoint) throws Exception {
        java.net.http.HttpResponse<String> initialize = post(endpoint, null,
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-06-18\","
                        + "\"capabilities\":{},\"clientInfo\":{\"name\":\"it\",\"version\":\"1\"}}}");
        String sessionId = initialize.headers().firstValue("mcp-session-id").orElseThrow();
        post(endpoint, sessionId, NOTIFICATION);
        return sessionId;
    }

    private static java.net.http.HttpResponse<String> post(String endpoint, String sessionId, String body)
            throws Exception {
        java.net.http.HttpRequest.Builder request = java.net.http.HttpRequest.newBuilder(URI.create(endpoint))
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .header("Authorization", "Bearer alice-token");
        if (sessionId != null) {
            request.header("Mcp-Session-Id", sessionId);
        }
        return HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(5))
                .build().send(request.build(), java.net.http.HttpResponse.BodyHandlers.ofString());
    }
}
