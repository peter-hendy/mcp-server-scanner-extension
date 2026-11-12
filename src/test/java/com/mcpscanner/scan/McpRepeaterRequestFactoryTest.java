package com.mcpscanner.scan;

import com.mcpscanner.testutil.RenderedRequests;
import com.mcpscanner.testutil.PlainFraming;
import com.mcpscanner.client.McpScannerSession;
import com.mcpscanner.mcp.McpPromptDefinition;
import com.mcpscanner.mcp.McpResourceDefinition;
import com.mcpscanner.mcp.McpResourceTemplateDefinition;
import com.mcpscanner.mcp.McpToolDefinition;
import com.mcpscanner.mcp.PromptArgument;
import com.mcpscanner.testutil.MontoyaTestFactory;
import com.mcpscanner.scan.request.ClientRegistrationRequestFactory;
import com.mcpscanner.scan.request.McpHttpRequestFactory;
import com.mcpscanner.scan.request.McpMessage;
import com.mcpscanner.scan.request.RequestFamily;
import com.mcpscanner.mcp.McpObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mcpscanner.scan.request.RequestGenerator;
import com.mcpscanner.scan.request.ScanRequestRenderer;
import burp.api.montoya.http.HttpService;
import burp.api.montoya.internal.ObjectFactoryLocator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import org.mockito.ArgumentCaptor;

class McpRepeaterRequestFactoryTest {

    @BeforeEach
    void installMontoyaFactory() {
        MontoyaTestFactory.install();
    }

    @Test
    void sendsEachInventoryKindWithOneSessionHeaderAndManagedProvenance() {
        McpScannerSession session = mock(McpScannerSession.class);
        when(session.resolvedEndpoint()).thenReturn("https://mcp.wix.com/mcp");
        when(session.scannerHeaders()).thenReturn(Map.of("Mcp-Session-Id", "session-1",
                "MCP-Protocol-Version", "2025-06-18", "Authorization", "Bearer private"));
        McpRepeaterRequestFactory factory = factory(session, () -> 17L,
                () -> ScanConnection.unbound(ignored -> Map.of(
                        "Mcp-Session-Id", "session-1",
                        "MCP-Protocol-Version", "2025-06-18",
                        "X-Mcp-Scanner-Audit-Capability", "signed")));

        List<burp.api.montoya.http.message.requests.HttpRequest> built = new java.util.ArrayList<>();
        List<String> raws = new java.util.ArrayList<>();
        when(ObjectFactoryLocator.FACTORY.httpRequest(any(HttpService.class), anyString())).thenAnswer(call -> {
            String raw = call.getArgument(1);
            raws.add(raw);
            var request = mock(burp.api.montoya.http.message.requests.HttpRequest.class);
            var headerNames = raw.lines().filter(line -> line.contains(": "))
                    .map(line -> line.substring(0, line.indexOf(": "))).toList();
            var headerMocks = headerNames.stream().map(name -> {
                var header = mock(burp.api.montoya.http.message.HttpHeader.class);
                when(header.name()).thenReturn(name);
                when(header.value()).thenReturn("value");
                return header;
            }).toList();
            when(request.headers()).thenReturn(headerMocks);
            when(request.withAddedHeader(anyString(), anyString())).thenReturn(request);
            when(request.withUpdatedHeader(anyString(), anyString())).thenReturn(request);
            built.add(request);
            return request;
        });
        factory.requestFor(new ScanTarget.Tool(new McpToolDefinition("echo", "", "{\"type\":\"object\"}")));
        factory.requestFor(new ScanTarget.Resource(new McpResourceDefinition("ui://widget/site-builder.html", "widget", "", "text/html")));
        factory.requestFor(new ScanTarget.ResourceTemplate(new McpResourceTemplateDefinition("ui://widget/{name}", "widget", "", "text/html")));
        factory.requestFor(new ScanTarget.Prompt(new McpPromptDefinition("summarize", "", List.of())));
        assertThat(raws).hasSize(4);
        for (String request : raws) {
            assertThat(request.lines().filter(line -> line.toLowerCase(Locale.ROOT)
                    .startsWith("mcp-session-id:")).count()).isEqualTo(1);
            assertThat(request).contains("MCP-Protocol-Version: 2025-06-18")
                    .doesNotContain("Bearer private");
        }
        for (var request : built) {
            verify(request).withUpdatedHeader("Mcp-Session-Id", "session-1");
            verify(request).withUpdatedHeader("MCP-Protocol-Version", "2025-06-18");
            verify(request).withAddedHeader("X-Mcp-Scanner-Audit-Capability", "signed");
        }
    }

    @Test
    void buildsCallsForEveryInventoryKindUsingConnectedSession() {
        McpScannerSession session = mock(McpScannerSession.class);
        when(session.resolvedEndpoint()).thenReturn("https://example.com/mcp?tenant=a");
        when(session.scannerHeaders()).thenReturn(Map.of(
                "Authorization", "Bearer private", "Mcp-Session-Id", "session-1",
                "MCP-Protocol-Version", "2025-06-18"));
        McpRepeaterRequestFactory factory = factory(session, new AtomicLong(2)::getAndIncrement,
                McpRepeaterRequestFactoryTest::unboundConnection);

        RenderedRequests rendered = RenderedRequests.capture();
        factory.requestFor(new ScanTarget.Tool(new McpToolDefinition("echo", "", "{\"type\":\"object\",\"properties\":{\"text\":{\"type\":\"string\"}}")));
        factory.requestFor(new ScanTarget.Resource(new McpResourceDefinition("file:///a", "a", "", "text/plain")));
        factory.requestFor(new ScanTarget.ResourceTemplate(new McpResourceTemplateDefinition("file:///{path}", "files", "", "text/plain")));
        factory.requestFor(new ScanTarget.Prompt(
                new McpPromptDefinition("summarize", "", List.of(new PromptArgument("text", "", true)))));
        assertThat(rendered.raw()).hasSize(4);
        String tool = rendered.raw().get(0);
        String resource = rendered.raw().get(1);
        String template = rendered.raw().get(2);
        String prompt = rendered.raw().get(3);

        assertThat(tool).contains("POST /mcp?tenant=a", "Mcp-Session-Id: session-1", "MCP-Protocol-Version: 2025-06-18", "\"method\":\"tools/call\"", "\"name\":\"echo\"", "\"id\":2");
        assertThat(resource).contains("\"method\":\"resources/read\"", "file:///a", "\"id\":3");
        assertThat(template).contains("\"method\":\"resources/read\"", "\"id\":4").doesNotContain("{path}");
        assertThat(prompt).contains("\"method\":\"prompts/get\"", "\"name\":\"summarize\"", "\"text\":\"test\"", "\"id\":5");
        assertThat(tool).doesNotContain("Bearer private");
    }

    @Test
    void rejectsDisconnectedSession() {
        McpScannerSession session = mock(McpScannerSession.class);
        McpRepeaterRequestFactory factory = factory(session, () -> 1L,
                McpRepeaterRequestFactoryTest::unboundConnection);

        assertThatThrownBy(() -> factory.requestFor(new ScanTarget.Resource(new McpResourceDefinition("file:///a", "a", "", ""))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not connected");
    }

    @Test
    void targetsHttpAndHttpsRepeaterRequestsWithTheirEndpointServices() {
        McpScannerSession session = mock(McpScannerSession.class);
        when(session.resolvedEndpoint()).thenReturn("http://localhost:8000/mcp", "https://example.com/mcp");
        when(session.scannerHeaders()).thenReturn(Map.of());
        McpRepeaterRequestFactory factory = factory(session, () -> 1L,
                McpRepeaterRequestFactoryTest::unboundConnection);

        RenderedRequests rendered = RenderedRequests.capture();
        factory.requestFor(new ScanTarget.Tool(new McpToolDefinition("echo", "", "{\"type\":\"object\"}")));
        factory.requestFor(new ScanTarget.Tool(new McpToolDefinition("echo", "", "{\"type\":\"object\"}")));

        assertThat(rendered.services()).satisfiesExactly(
                http -> assertThat(http).extracting(HttpService::host, HttpService::port, HttpService::secure)
                        .containsExactly("localhost", 8000, false),
                https -> assertThat(https).extracting(HttpService::host, HttpService::port, HttpService::secure)
                        .containsExactly("example.com", 443, true));
        assertThat(rendered.raw()).allSatisfy(request ->
                assertThat(request).startsWith("POST /mcp HTTP/1.1\r\n"));
    }

    @Test
    void rendersARequestOfAnInventoryFreeFamilyOnTheConnectedSession() {
        McpScannerSession session = mock(McpScannerSession.class);
        when(session.resolvedEndpoint()).thenReturn("https://example.com/mcp");
        when(session.scannerHeaders()).thenReturn(Map.of("Mcp-Session-Id", "session-1"));
        McpRepeaterRequestFactory factory = factory(session, () -> 9L,
                McpRepeaterRequestFactoryTest::unboundConnection);

        RenderedRequests rendered = RenderedRequests.capture();
        factory.requestFor(com.mcpscanner.scan.request.RequestFamily.PING);

        assertThat(rendered.raw()).singleElement().satisfies(ping -> assertThat(ping)
                .contains("\"method\":\"ping\"", "Mcp-Session-Id: session-1"));
    }

    @Test
    void rendersAGivenMessageOnTheConnectedSessionThroughTheFramer() {
        McpScannerSession session = mock(McpScannerSession.class);
        when(session.resolvedEndpoint()).thenReturn("https://example.com/mcp");
        when(session.scannerHeaders()).thenReturn(Map.of("Mcp-Session-Id", "session-1"));
        McpRepeaterRequestFactory factory = factory(session, () -> 11L,
                McpRepeaterRequestFactoryTest::unboundConnection);
        ObjectNode params = McpObjectMapper.INSTANCE
                .createObjectNode().put("uri", "notes://1");

        RenderedRequests rendered = RenderedRequests.capture();
        factory.requestFor(McpMessage.call(
                RequestFamily.RESOURCES_READ, "resources/read", params));

        assertThat(rendered.raw()).singleElement().satisfies(read -> assertThat(read)
                .contains("\"id\":11", "\"method\":\"resources/read\"", "\"uri\":\"notes://1\"",
                        "Mcp-Session-Id: session-1"));
    }

    private static McpRepeaterRequestFactory factory(McpScannerSession session, LongSupplier ids,
                                                     Supplier<ScanConnection> connection) {
        return new McpRepeaterRequestFactory(scanSession(session), RequestGenerator.standard(),
                new ScanRequestRenderer(new McpHttpRequestFactory(ids, PlainFraming.framer())::build, new ClientRegistrationRequestFactory()),
                connection);
    }

    private static ScanSession scanSession(McpScannerSession session) {
        return ScanSession.of(session::resolvedEndpoint, session::scannerHeaders);
    }

    private static ScanConnection unboundConnection() {
        return ScanConnection.unbound(headers -> Map.of());
    }
}
