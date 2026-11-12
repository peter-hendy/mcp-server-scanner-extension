package com.mcpscanner.integration;

import burp.api.montoya.core.ByteArray;
import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.logging.Logging;
import burp.api.montoya.scanner.audit.insertionpoint.AuditInsertionPoint;
import burp.api.montoya.scanner.audit.insertionpoint.AuditInsertionPointType;
import com.fasterxml.jackson.databind.JsonNode;
import com.mcpscanner.auth.NoAuthStrategy;
import com.mcpscanner.client.ConnectResult;
import com.mcpscanner.client.McpClientManager;
import com.mcpscanner.client.McpServerConfig;
import com.mcpscanner.client.TransportType;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.mcp.BurpNativeJsonRpcFields;
import com.mcpscanner.mcp.McpObjectMapper;
import com.mcpscanner.mcp.McpOperationFraming;
import com.mcpscanner.scan.ScanInventory;
import com.mcpscanner.scan.UriTemplateExpansion;
import com.mcpscanner.scan.field.McpFieldGenerator;
import com.mcpscanner.scan.field.TemplateExpansionRegistry;
import com.mcpscanner.scan.request.GeneratedScan;
import com.mcpscanner.scan.request.McpHttpRequestFactory;
import com.mcpscanner.scan.request.McpMessage;
import com.mcpscanner.scan.request.RequestFamily;
import com.mcpscanner.scan.request.RequestGenerator;
import com.mcpscanner.scan.request.ServerCapabilities;
import com.mcpscanner.scan.request.ServerSurface;
import com.mcpscanner.testutil.MontoyaTestFactory;
import com.mcpscanner.testutil.RawHttpRequestStub;
import com.mcpscanner.core.protocol.McpRequests;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@EnabledIfEnvironmentVariable(named = "MCP_E2E_IT", matches = "1")
class RequestAndFieldGeneratorIT {

    private static final String MARKER = "mcpgenmarker";
    private static final String FIELD_PREFIX = "MCP field: ";
    private static final String VARIABLE_PREFIX = "MCP template variable: ";
    private static final String[] SAFE_EXCEPT_NO_AUTH = {
            "--default-mode", "safe", "--vuln", "unauthenticated_execution=vulnerable"};
    private static final Set<String> RESTRICTED_HEADERS = Set.of("host", "content-length", "connection");
    private static final Pattern ARRAY_INDEX = Pattern.compile("\\[(\\d+)]");
    private static final List<String> EVERY_VERSION = List.of();

    private final HttpClient client = McpCheckItSupport.realHttpClient();
    private final AtomicLong requestIds = new AtomicLong(10_000);
    private final TemplateExpansionRegistry templateExpansions = new TemplateExpansionRegistry();
    private final McpFieldGenerator bodyFields = new McpFieldGenerator(() -> false, templateExpansions, BurpNativeJsonRpcFields.ABSENT);
    private final McpFieldGenerator allFields = new McpFieldGenerator(() -> true, templateExpansions, BurpNativeJsonRpcFields.ABSENT);

    @BeforeAll
    static void installMontoya() {
        MontoyaTestFactory.install();
    }

    static Stream<Arguments> servedVersions() {
        return Stream.of(
                Arguments.of(List.of("2024-11-05"), "2024-11-05"),
                Arguments.of(List.of("2025-03-26"), "2025-03-26"),
                Arguments.of(List.of("2025-06-18"), "2025-06-18"),
                Arguments.of(List.of("2025-11-25"), "2025-11-25"),
                Arguments.of(List.of("2025-06-18", "2025-03-26"), "2025-06-18"),
                Arguments.of(EVERY_VERSION, McpRequests.MODERN_VERSION));
    }

    @ParameterizedTest(name = "serving {0} negotiates {1}")
    @MethodSource("servedVersions")
    void everyFamilyOfTheNegotiatedVersionAndEveryGeneratedFieldReachesTheServer(
            List<String> served, String negotiated) throws Exception {
        try (McpCheckItSupport.RunningServer server = McpCheckItSupport.startServer(serving(served))) {
            Connected connected = connect(server.port());
            List<HttpRequest> sent = sendAll(server.port(), connected);
            List<JsonNode> log = requestLog(server.port());

            assertThat(connected.negotiatedVersion()).isEqualTo(negotiated);
            assertThat(families(connected.scan())).isEqualTo(familiesOfAnUnauthenticatedServerDefinedIn(negotiated));
            assertThat(log).hasSameSizeAs(sent);
            for (int i = 0; i < sent.size(); i++) {
                assertServerSawEveryField(connected, connected.messages().get(i), sent.get(i), log.get(i));
            }
        }
    }

    @ParameterizedTest(name = "serving {0} negotiates {1}")
    @MethodSource("servedVersions")
    void everyBodyAndHeaderInsertionPointCarriesItsPayloadToTheRightPlace(
            List<String> served, String negotiated) throws Exception {
        try (McpCheckItSupport.RunningServer server = McpCheckItSupport.startServer(serving(served))) {
            Connected connected = connect(server.port());
            int checked = 0;
            int variablePointsChecked = 0;
            for (McpMessage message : connected.messages()) {
                HttpRequest base = render(server.port(), message, connected);
                for (AuditInsertionPoint point : allFields.fieldsOf(base)) {
                    clearRequestLog(server.port());
                    send(server.port(), point.buildHttpRequestWithPayload(ByteArray.byteArray(MARKER)));
                    JsonNode entry = requestLog(server.port()).get(0);
                    assertPayloadLanded(message, point, entry);
                    checked++;
                    variablePointsChecked += point.name().startsWith(VARIABLE_PREFIX) ? 1 : 0;
                }
            }
            assertThat(connected.negotiatedVersion()).isEqualTo(negotiated);
            assertThat(checked).isGreaterThan(connected.messages().size());
            assertThat(variablePointsChecked).as("template variable insertion points exercised").isPositive();
        }
    }


    @ParameterizedTest(name = "serving {0}")
    @MethodSource("servedVersions")
    void familiesTheServerDoesNotAdvertiseAreNotSent(List<String> served, String negotiated) throws Exception {
        List<String> arguments = new ArrayList<>(List.of(serving(served)));
        arguments.addAll(List.of("--disable-family", "completion", "--disable-family", "logging",
                "--disable-family", "subscribe"));
        try (McpCheckItSupport.RunningServer server = McpCheckItSupport.startServer(arguments.toArray(String[]::new))) {
            Connected connected = connect(server.port());
            sendAll(server.port(), connected);
            Set<String> loggedMethods = new HashSet<>();
            requestLog(server.port()).forEach(entry -> loggedMethods.add(entry.path("messages").get(0)
                    .path("method").asText()));

            Set<RequestFamily> expected = familiesOfAnUnauthenticatedServerDefinedIn(negotiated);
            expected.removeAll(EnumSet.of(RequestFamily.COMPLETION, RequestFamily.LOGGING, RequestFamily.SUBSCRIBE));
            assertThat(families(connected.scan())).isEqualTo(expected);
            assertThat(loggedMethods).doesNotContain("completion/complete", "logging/setLevel",
                    "resources/subscribe", "resources/unsubscribe")
                    .contains("tools/call", "resources/read", "prompts/get", "tools/list", "resources/list",
                            "resources/templates/list", "prompts/list", "notifications/cancelled",
                            "notifications/progress", "notifications/roots/list_changed", "ping", "server/discover");
        }
    }

    private static String[] serving(List<String> versions) {
        List<String> arguments = new ArrayList<>(List.of(SAFE_EXCEPT_NO_AUTH));
        versions.forEach(version -> arguments.addAll(List.of("--protocol-version", version)));
        return arguments.toArray(String[]::new);
    }

    private static Set<RequestFamily> familiesOfAnUnauthenticatedServerDefinedIn(String version) {
        Set<RequestFamily> families = EnumSet.noneOf(RequestFamily.class);
        for (RequestFamily family : RequestFamily.values()) {
            if (family.definedIn(version) && family != RequestFamily.CLIENT_REGISTRATION) {
                families.add(family);
            }
        }
        return families;
    }

    private void assertServerSawEveryField(Connected connected, McpMessage message, HttpRequest sent, JsonNode entry) {
        JsonNode logged = entry.path("messages").get(0);
        Set<String> expectedPaths = new HashSet<>();
        bodyFields.fieldsOf(sent).stream().filter(point -> point.name().startsWith(FIELD_PREFIX))
                .forEach(point -> expectedPaths.add(fieldPath(point)));
        Set<String> loggedPaths = new HashSet<>();
        logged.path("param_paths").forEach(path -> loggedPaths.add(path.asText()));
        Set<String> loggedHeaders = new HashSet<>();
        entry.path("headers").forEach(header -> loggedHeaders.add(header.get(0).asText().toLowerCase(Locale.ROOT)));

        assertThat(logged.path("method").asText()).as("method").isEqualTo(message.method());
        assertThat(logged.path("notification").asBoolean()).as(message.subject()).isEqualTo(message.notification());
        assertThat(loggedPaths).as("param paths of %s", message.subject()).isEqualTo(expectedPaths);
        if (McpRequests.MODERN_VERSION.equals(connected.negotiatedVersion())) {
            assertThat(loggedHeaders).as("headers of %s", message.subject())
                    .contains("mcp-protocol-version", "mcp-method").doesNotContain("mcp-session-id");
        } else {
            assertThat(loggedHeaders).as("headers of %s", message.subject())
                    .contains("mcp-session-id", "mcp-protocol-version").doesNotContain("mcp-method", "mcp-name");
        }
        assertThat(headerValues(entry, "mcp-protocol-version")).as("version of %s", message.subject())
                .containsExactly(connected.negotiatedVersion());
        assertThat(entry.path("status").asInt()).as("status of %s", message.subject())
                .isEqualTo(message.notification() ? 202 : 200);
    }

    private void assertPayloadLanded(McpMessage message, AuditInsertionPoint point, JsonNode entry) {
        String where = message.subject() + " / " + point.name();
        if (point.type() == AuditInsertionPointType.HEADER) {
            String header = point.name().substring(point.name().indexOf(": ") + 2);
            assertThat(headerValues(entry, header)).as(where).containsExactly(MARKER);
            return;
        }
        JsonNode params = entry.path("messages").get(0).path("params");
        if (point.name().startsWith(VARIABLE_PREFIX)) {
            assertThat(params.path("uri").asText()).as(where).isEqualTo(uriWithVariableReplaced(message, point));
            return;
        }
        assertThat(valueAt(params, fieldPath(point)).asText()).as(where).isEqualTo(MARKER);
    }

    private static String uriWithVariableReplaced(McpMessage message, AuditInsertionPoint point) {
        UriTemplateExpansion.Result expansion = message.templateExpansion().orElseThrow();
        UriTemplateExpansion.Variable variable = expansion.variables().stream()
                .filter(candidate -> point.name().equals(VARIABLE_PREFIX + candidate.name()))
                .findFirst().orElseThrow();
        byte[] uri = expansion.expandedUri().getBytes(StandardCharsets.UTF_8);
        return new String(uri, 0, variable.startInclusive(), StandardCharsets.UTF_8) + MARKER
                + new String(uri, variable.endExclusive(), uri.length - variable.endExclusive(),
                StandardCharsets.UTF_8);
    }

    private static List<String> headerValues(JsonNode entry, String header) {
        List<String> values = new ArrayList<>();
        entry.path("headers").forEach(pair -> {
            if (pair.get(0).asText().equalsIgnoreCase(header)) {
                values.add(pair.get(1).asText());
            }
        });
        return values;
    }

    private static JsonNode valueAt(JsonNode params, String path) {
        return resolve(params, path.substring("params".length()));
    }

    private static JsonNode resolve(JsonNode node, String remaining) {
        if (remaining.isEmpty()) {
            return node;
        }
        Matcher index = ARRAY_INDEX.matcher(remaining);
        if (index.lookingAt()) {
            return resolve(node.path(Integer.parseInt(index.group(1))), remaining.substring(index.end()));
        }
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names.stream()
                .filter(name -> remaining.equals("." + name) || remaining.startsWith("." + name + ".")
                        || remaining.startsWith("." + name + "["))
                .map(name -> resolve(node.path(name), remaining.substring(name.length() + 1)))
                .filter(found -> !found.isMissingNode())
                .findFirst()
                .orElse(com.fasterxml.jackson.databind.node.MissingNode.getInstance());
    }

    private static String fieldPath(AuditInsertionPoint point) {
        return point.name().substring(FIELD_PREFIX.length());
    }

    private Connected connect(int port) {
        McpClientManager manager = new McpClientManager(new McpEventLog(null));
        ConnectResult result = manager.connect(new McpServerConfig(McpCheckItSupport.mcpEndpoint(port),
                TransportType.AUTO, new NoAuthStrategy()));
        String negotiatedVersion = manager.scannerSession().negotiatedProtocolVersion();
        ServerSurface surface = new ServerSurface(
                new ScanInventory(result.tools(), result.resources(), result.resourceTemplates(), result.prompts()),
                new ServerCapabilities(result.serverMetadata().capabilities()), negotiatedVersion);
        GeneratedScan scan = RequestGenerator.standard().scanAll(surface, family -> true);
        scan.messages().forEach(message -> message.templateExpansion().ifPresent(templateExpansions::record));
        return new Connected(scan, Map.copyOf(manager.scannerSession().scannerHeaders()), negotiatedVersion,
                manager.scannerSession());
    }

    private List<HttpRequest> sendAll(int port, Connected connected) throws Exception {
        clearRequestLog(port);
        List<HttpRequest> sent = new ArrayList<>();
        for (McpMessage message : connected.messages()) {
            HttpRequest request = render(port, message, connected);
            send(port, request);
            sent.add(request);
        }
        return sent;
    }

    private HttpRequest render(int port, McpMessage message, Connected connected) {
        McpHttpRequestFactory factory = new McpHttpRequestFactory(requestIds::incrementAndGet, connected.framer());
        return RawHttpRequestStub.fromRaw(factory.raw(McpCheckItSupport.mcpEndpoint(port), message,
                connected.sessionHeaders()));
    }

    private void send(int port, HttpRequest request) throws Exception {
        java.net.http.HttpRequest.Builder builder = java.net.http.HttpRequest
                .newBuilder(URI.create(McpCheckItSupport.mcpEndpoint(port)))
                .timeout(Duration.ofSeconds(20))
                .POST(java.net.http.HttpRequest.BodyPublishers.ofByteArray(request.body().getBytes()));
        for (HttpHeader header : request.headers()) {
            if (!RESTRICTED_HEADERS.contains(header.name().toLowerCase(Locale.ROOT))) {
                builder.header(header.name(), header.value());
            }
        }
        client.send(builder.build(), HttpResponse.BodyHandlers.discarding());
    }

    private List<JsonNode> requestLog(int port) throws Exception {
        HttpResponse<String> response = client.send(java.net.http.HttpRequest.newBuilder(inspection(port)).GET()
                .build(), HttpResponse.BodyHandlers.ofString());
        List<JsonNode> entries = new ArrayList<>();
        McpObjectMapper.INSTANCE.readTree(response.body()).forEach(entries::add);
        return entries;
    }

    private void clearRequestLog(int port) throws Exception {
        client.send(java.net.http.HttpRequest.newBuilder(inspection(port)).DELETE().build(),
                HttpResponse.BodyHandlers.discarding());
    }

    private static URI inspection(int port) {
        return URI.create("http://" + McpCheckItSupport.HOST + ":" + port + "/_test/requests");
    }

    private static Set<RequestFamily> families(GeneratedScan scan) {
        Set<RequestFamily> families = EnumSet.noneOf(RequestFamily.class);
        scan.messages().forEach(message -> families.add(message.family()));
        return families;
    }

    private record Connected(GeneratedScan scan, Map<String, String> sessionHeaders, String negotiatedVersion,
                             McpOperationFraming.Framer framer) {

        List<McpMessage> messages() {
            return scan.messages().stream().map(McpMessage.class::cast).toList();
        }
    }
}
