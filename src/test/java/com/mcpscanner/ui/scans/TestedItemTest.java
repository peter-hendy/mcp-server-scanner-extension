package com.mcpscanner.ui.scans;

import burp.api.montoya.core.ByteArray;
import burp.api.montoya.core.Marker;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.scanner.audit.issues.AuditIssue;
import burp.api.montoya.scanner.audit.issues.AuditIssueSeverity;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestedItemTest {

    @BeforeEach
    void setUp() {
        MontoyaTestFactory.install();
    }

    @Test
    void namesAToolCallAndTheArgumentItsPayloadWentInto() {
        HttpRequest request = mcp("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":"
                + "{\"name\":\"run_query\",\"arguments\":{\"sql\":\"x' OR 1=1\"}}}");

        assertThat(untested(issueWith(markedAt(request, "x' OR 1=1")))).isEqualTo("tools/call run_query · arguments.sql");
    }

    @Test
    void namesAHeaderItsPayloadWentInto() {
        HttpRequest request = mcp("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}").withHeader("Mcp-Method", "PAYLOAD");

        assertThat(untested(issueWith(markedAt(request, "PAYLOAD")))).isEqualTo("ping · Mcp-Method header");
    }

    @Test
    void namesTheItemAloneWhenNoPayloadIsMarked() {
        HttpRequest read = mcp("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"resources/read\",\"params\":{\"uri\":\"file:///x\"}}");
        HttpRequest prompt = mcp("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"prompts/get\",\"params\":{\"name\":\"summarize\"}}");

        assertThat(untested(issueWith(unmarked(read)))).isEqualTo("resources/read file:///x");
        assertThat(untested(issueWith(unmarked(prompt)))).isEqualTo("prompts/get summarize");
    }

    @Test
    void namesAQueryParameterInTheRequestLineAndNothingForTheRestOfIt() {
        HttpRequest query = raw("POST /mcp?token=PAYLOAD HTTP/1.1");
        HttpRequest path = raw("POST /PAYLOAD/mcp HTTP/1.1");

        assertThat(untested(issueWith(markedAt(query, "PAYLOAD")))).isEqualTo("ping · token query parameter");
        assertThat(untested(issueWith(markedAt(path, "PAYLOAD")))).isEqualTo("ping");
    }

    @Test
    void namesATopLevelJsonFieldOfANonMcpBody() {
        HttpRequest registration = HttpRequest.httpRequestFromUrl("https://mcp.example/register").withMethod("POST")
                .withBody("{\"client_name\":\"PAYLOAD\",\"redirect_uris\":[\"http://x\"]}");

        assertThat(untested(issueWith(markedAt(registration, "PAYLOAD")))).isEqualTo("POST /register · client_name");
    }

    @Test
    void aVeryLargeBodyIsNamedByMethodAndPathWithoutBeingParsed() {
        String huge = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"big\","
                + "\"arguments\":{\"blob\":\"" + "x".repeat(TestedItem.MAX_PARSED_BODY_BYTES) + "PAYLOAD\"}}}";

        assertThat(untested(issueWith(markedAt(mcp(huge), "PAYLOAD")))).isEqualTo("POST /mcp");
    }

    @Test
    void namesTheScannedOperationWhenThePayloadReplacedTheToolName() {
        HttpRequest request = mcp("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":"
                + "{\"name\":\"http://w52z.oastify.com\",\"arguments\":{\"host\":\"a\"}}}");

        assertThat(TestedItem.of(issueWith(markedAt(request, "http://w52z.oastify.com")),
                Optional.of("tools/call ping_host"))).isEqualTo("tools/call ping_host · name");
    }

    @Test
    void namesTheScannedOperationWhenThePayloadReplacedAResourceUriOrPromptName() {
        HttpRequest read = mcp("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"resources/read\",\"params\":{\"uri\":\"PAYLOAD\"}}");
        HttpRequest prompt = mcp("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"prompts/get\",\"params\":{\"name\":\"PAYLOAD\"}}");

        assertThat(TestedItem.of(issueWith(markedAt(read, "PAYLOAD")), Optional.of("resources/read file:///x")))
                .isEqualTo("resources/read file:///x · uri");
        assertThat(TestedItem.of(issueWith(markedAt(prompt, "PAYLOAD")), Optional.of("prompts/get summarize")))
                .isEqualTo("prompts/get summarize · name");
    }

    @Test
    void aVeryLargeBodyIsNamedByTheScannedOperationWithoutBeingParsed() {
        String huge = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"big\","
                + "\"arguments\":{\"blob\":\"" + "x".repeat(TestedItem.MAX_PARSED_BODY_BYTES) + "PAYLOAD\"}}}";

        assertThat(TestedItem.of(issueWith(markedAt(mcp(huge), "PAYLOAD")), Optional.of("tools/call big")))
                .isEqualTo("tools/call big");
    }

    @Test
    void anIssueWithoutEvidenceTestedNothingNamed() {
        assertThat(untested(Issues.issue("Host check", AuditIssueSeverity.LOW))).isEmpty();
    }

    private static String untested(AuditIssue issue) {
        return TestedItem.of(issue, Optional.empty());
    }

    private static HttpRequest raw(String requestLine) {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}";
        String head = requestLine + "\r\nHost: mcp.example\r\n\r\n";
        ByteArray wire = ByteArray.byteArray(head + body);
        ByteArray bodyBytes = ByteArray.byteArray(body);
        HttpRequest request = mock(HttpRequest.class);
        when(request.toByteArray()).thenReturn(wire);
        when(request.bodyOffset()).thenReturn(head.length());
        when(request.body()).thenReturn(bodyBytes);
        when(request.bodyToString()).thenReturn(body);
        when(request.method()).thenReturn("POST");
        when(request.path()).thenReturn(requestLine.split(" ")[1]);
        when(request.toString()).thenReturn(head + body);
        return request;
    }

    private static HttpRequest mcp(String body) {
        return HttpRequest.httpRequestFromUrl("https://mcp.example/mcp").withMethod("POST").withBody(body);
    }

    private static HttpRequestResponse markedAt(HttpRequest request, String payload) {
        int start = new String(request.toByteArray().getBytes(), StandardCharsets.ISO_8859_1).indexOf(payload);
        return HttpRequestResponse.httpRequestResponse(request, null)
                .withRequestMarkers(List.of(Marker.marker(start, start + payload.length())));
    }

    private static HttpRequestResponse unmarked(HttpRequest request) {
        return HttpRequestResponse.httpRequestResponse(request, null);
    }

    private static AuditIssue issueWith(HttpRequestResponse exchange) {
        return Issues.issue("Issue", AuditIssueSeverity.HIGH, "<p>d</p>", List.of(exchange));
    }
}
