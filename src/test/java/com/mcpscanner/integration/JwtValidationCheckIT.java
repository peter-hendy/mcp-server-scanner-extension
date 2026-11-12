package com.mcpscanner.integration;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.scanner.audit.issues.AuditIssue;
import burp.api.montoya.scanner.audit.issues.AuditIssueConfidence;
import burp.api.montoya.scanner.audit.issues.AuditIssueSeverity;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcpscanner.auth.BearerTokenAuthStrategy;
import com.mcpscanner.checks.api.AccessLedger;
import com.mcpscanner.checks.api.CheckCatalog;
import com.mcpscanner.checks.api.CheckRunner;
import com.mcpscanner.checks.api.CheckServices;
import com.mcpscanner.checks.api.PrincipalRoster;
import com.mcpscanner.checks.api.PrincipalListings;
import com.mcpscanner.checks.api.McpCheck;
import com.mcpscanner.cvetargets.requests.VerbatimHeaderHttp;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.scan.McpInsertionPointProvider;
import com.mcpscanner.testutil.ImmutableHttpRequest;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "MCP_E2E_IT", matches = "1")
class JwtValidationCheckIT {

    private static final List<String> JWT_VULNS = List.of("jwt_forged_signature", "jwt_alg_none",
            "jwt_hs256_key_confusion", "jwt_empty_key_unknown_kid", "jwt_wrong_audience", "jwt_wrong_issuer",
            "jwt_expired", "unauthenticated_execution");
    private static final String ECHO_CALL = "{\"jsonrpc\":\"2.0\",\"id\":41,\"method\":\"tools/call\","
            + "\"params\":{\"name\":\"echo\",\"arguments\":{\"message\":\"jwt-validation-it\"}}}";
    private static final ObjectMapper JSON = new ObjectMapper();

    private static McpCheckItSupport.RunningServer server;

    @BeforeAll
    static void startServer() throws Exception {
        server = McpCheckItSupport.startServer("--default-mode", "safe");
    }

    @AfterAll
    static void stopServer() throws Exception {
        if (server != null) {
            server.close();
        }
    }

    @BeforeEach
    void allSafe() throws Exception {
        MontoyaTestFactory.install();
        Map<String, String> modes = new LinkedHashMap<>();
        JWT_VULNS.forEach(vuln -> modes.put(vuln, "safe"));
        setModes(modes);
    }

    @ParameterizedTest
    @CsvSource({
            "jwt_forged_signature, freshly generated RSA key",
            "jwt_alg_none, alg: none",
            "jwt_hs256_key_confusion, HMAC secret is the public key",
            "jwt_empty_key_unknown_kid, unknown key id (kid) and an empty HMAC secret"})
    void signatureWeaknessFiresInVulnerableModeAndIsSilentInSafeMode(String vuln, String expectedArm)
            throws Exception {
        assertThat(scan()).isEmpty();

        setModes(Map.of(vuln, "vulnerable"));
        List<AuditIssue> issues = scan();

        assertThat(issues).singleElement().satisfies(issue -> {
            assertThat(issue.severity()).isEqualTo(AuditIssueSeverity.HIGH);
            assertThat(issue.confidence()).isEqualTo(AuditIssueConfidence.FIRM);
            assertThat(issue.detail()).contains(expectedArm).contains("appear to be checked");
        });

        setModes(Map.of(vuln, "safe"));
        assertThat(scan()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"jwt_wrong_audience", "jwt_wrong_issuer", "jwt_expired"})
    void claimWeaknessAloneIsNotDetectableWithoutATrustedSigningKey(String vuln) throws Exception {
        setModes(Map.of(vuln, "vulnerable"));

        assertThat(scan()).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
            "jwt_wrong_audience, audience (aud)",
            "jwt_wrong_issuer, issuer (iss)",
            "jwt_expired, expiry (exp)"})
    void claimWeaknessIsReportedOnTopOfAnAcceptedForgery(String vuln, String claim) throws Exception {
        setModes(Map.of("jwt_alg_none", "vulnerable", vuln, "vulnerable"));

        String detail = scan().get(0).detail();
        String unvalidated = detail.substring(detail.indexOf("does not validate"), detail.indexOf("appear to be checked"));

        assertThat(unvalidated).contains(claim);
        assertThat(detail.substring(detail.indexOf("appear to be checked"))).doesNotContain(claim);
    }

    @Test
    void anonymouslyOpenServerIsNotReportedAsAJwtFlaw() throws Exception {
        setModes(Map.of("unauthenticated_execution", "vulnerable", "jwt_alg_none", "vulnerable"));

        assertThat(scan()).isEmpty();
    }

    private static List<AuditIssue> scan() throws Exception {
        String token = mintToken();
        String sessionId = McpCheckItSupport.initializeSessionWithBearer(server.port(), token);
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Accept", "application/json, text/event-stream");
        headers.put("Mcp-Session-Id", sessionId);
        headers.put("Authorization", "Bearer " + token);
        HttpRequest request = ImmutableHttpRequest.post(McpCheckItSupport.mcpEndpoint(server.port()), headers,
                ECHO_CALL);
        VerbatimHeaderHttp http = new VerbatimHeaderHttp();
        HttpRequestResponse base = http.sendRequest(request);
        assertThat(base.response().statusCode()).isEqualTo((short) 200);

        CheckServices services = new CheckServices(McpEventLog.noop(), () -> new BearerTokenAuthStrategy(token),
                Optional::empty, Optional::empty,
                PrincipalRoster::empty, AccessLedger.noop(), PrincipalListings.unknown());
        McpCheck check = CheckCatalog.discover(services).find("jwt-validation").orElseThrow();
        return new CheckRunner(http, new McpInsertionPointProvider()::provideInsertionPoints)
                .run(check, List.of(base));
    }

    private static String mintToken() throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(java.net.http.HttpRequest.newBuilder(
                        URI.create(baseUrl() + "/_test/mint-token"))
                .header("Content-Type", "application/json")
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString("{}")).build(),
                HttpResponse.BodyHandlers.ofString());
        return JSON.readTree(response.body()).path("access_token").asText();
    }

    private static void setModes(Map<String, String> modes) throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(java.net.http.HttpRequest.newBuilder(
                        URI.create(baseUrl() + "/_test/vulns"))
                .header("Content-Type", "application/json")
                .PUT(java.net.http.HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(modes))).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
    }

    private static String baseUrl() {
        return "http://" + McpCheckItSupport.HOST + ":" + server.port();
    }
}
