package com.mcpscanner.proxy;

import burp.api.montoya.core.Annotations;
import burp.api.montoya.core.ToolSource;
import burp.api.montoya.core.ToolType;
import burp.api.montoya.http.HttpService;
import burp.api.montoya.http.handler.HttpRequestToBeSent;
import burp.api.montoya.http.handler.HttpResponseReceived;
import burp.api.montoya.http.handler.RequestToBeSentAction;
import burp.api.montoya.http.handler.ResponseReceivedAction;
import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.client.McpScannerSession;
import com.mcpscanner.client.TransportType;
import com.mcpscanner.proxy.observe.BurpTrafficObserver;
import com.mcpscanner.proxy.observe.NoopBurpTrafficObserver;
import com.mcpscanner.proxy.observe.ScannerFamilySwapPolicy;
import com.mcpscanner.proxy.observe.SwapAllMatchingTools;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class McpHttpHandlerTest {

    @Mock private McpScannerSession scannerSession;
    @Mock private SseProxyServer proxy;
    @Mock private HttpRequestToBeSent request;
    @Mock private HttpService httpService;
    @Mock private HttpRequest rewrittenRequest;
    @Mock private HttpResponseReceived response;
    @Mock private HttpRequest initiatingRequest;
    @Mock private Annotations annotations;
    @Mock private BurpTrafficObserver observer;
    @Mock private ToolSource toolSource;

    private McpHttpHandler handler;

    @BeforeAll
    static void installFactory() {
        MontoyaTestFactory.install();
    }

    @BeforeEach
    void setUp() {
        handler = new McpHttpHandler(scannerSession, proxy, new SwapAllMatchingTools(), observer);
    }

    @Test
    void rewritesStreamableHttpJsonRpcRequestToProxy() {
        when(scannerSession.transportType()).thenReturn(TransportType.STREAMABLE_HTTP);
        when(scannerSession.resolvedEndpoint()).thenReturn("http://localhost:3001/mcp");
        when(proxy.port()).thenReturn(9999);
        stubRequest("localhost", 3001, "/mcp");
        when(request.withService(any(HttpService.class))).thenReturn(rewrittenRequest);

        RequestToBeSentAction result = handler.handleHttpRequestToBeSent(request);

        assertThat(result.request()).isSameAs(rewrittenRequest);
        verify(request).withService(any(HttpService.class));
    }

    @Test
    void rewritesSseTransportRequestsToProxy() {
        when(scannerSession.transportType()).thenReturn(TransportType.SSE);
        when(scannerSession.resolvedEndpoint()).thenReturn("http://localhost:3001/message");
        when(proxy.port()).thenReturn(9999);
        stubRequest("localhost", 3001, "/message");
        when(request.withService(any(HttpService.class))).thenReturn(rewrittenRequest);

        RequestToBeSentAction result = handler.handleHttpRequestToBeSent(request);

        assertThat(result.request()).isSameAs(rewrittenRequest);
        verify(request).withService(any(HttpService.class));
    }

    @Test
    void rewritesHttpsRequestWithDefaultPortToProxy() {
        when(scannerSession.transportType()).thenReturn(TransportType.SSE);
        when(scannerSession.resolvedEndpoint()).thenReturn("https://example.com/messages?session_id=abc");
        when(proxy.port()).thenReturn(9999);
        stubRequest("example.com", 443, "/messages");
        when(request.withService(any(HttpService.class))).thenReturn(rewrittenRequest);

        RequestToBeSentAction result = handler.handleHttpRequestToBeSent(request);

        assertThat(result.request()).isSameAs(rewrittenRequest);
        verify(request).withService(any(HttpService.class));
    }

    @Test
    void stampsOriginalHostInAnnotationsNotesWhenRewriting() {
        when(scannerSession.transportType()).thenReturn(TransportType.STREAMABLE_HTTP);
        when(scannerSession.resolvedEndpoint()).thenReturn("https://mcp.example.com/v1/mcp");
        when(proxy.port()).thenReturn(9999);
        stubRequest("mcp.example.com", 443, "/v1/mcp");
        when(request.withService(any(HttpService.class))).thenReturn(rewrittenRequest);

        handler.handleHttpRequestToBeSent(request);

        verify(annotations).setNotes("→ mcp.example.com");
    }

    /**
     * Method-irrelevance regression: the handler must rewrite the MCP endpoint path regardless of
     * method (POST / GET / OPTIONS / HEAD). Only the path component participates in the match.
     */
    @Test
    void rewritesEvenIfMethodIsNotPost_whenPathMatchesEndpoint() {
        when(scannerSession.transportType()).thenReturn(TransportType.STREAMABLE_HTTP);
        when(scannerSession.resolvedEndpoint()).thenReturn("http://localhost:3001/mcp");
        when(proxy.port()).thenReturn(9999);
        stubRequest("localhost", 3001, "/mcp");
        when(request.withService(any(HttpService.class))).thenReturn(rewrittenRequest);

        RequestToBeSentAction result = handler.handleHttpRequestToBeSent(request);

        assertThat(result.request()).isSameAs(rewrittenRequest);
        verify(request).withService(any(HttpService.class));
    }

    /**
     * Query-string irrelevance: only the path component participates in the match. Burp's
     * parameter-pollution probes append arbitrary query params and must still be rewritten when the
     * path still equals the MCP endpoint path.
     */
    @Test
    void pathWithQueryStringStillMatchesWhenPathComponentMatches() {
        when(scannerSession.transportType()).thenReturn(TransportType.SSE);
        when(scannerSession.resolvedEndpoint()).thenReturn("http://localhost:3001/message?session=abc");
        when(proxy.port()).thenReturn(9999);
        stubRequest("localhost", 3001, "/message");
        when(request.withService(any(HttpService.class))).thenReturn(rewrittenRequest);

        RequestToBeSentAction result = handler.handleHttpRequestToBeSent(request);

        assertThat(result.request()).isSameAs(rewrittenRequest);
        verify(request).withService(any(HttpService.class));
    }

    /**
     * Body / content-type irrelevance: the handler doesn't inspect the body. Same-path requests are
     * rewritten regardless.
     */
    @Test
    void rewritesEvenIfContentTypeIsNotJson_whenPathMatchesEndpoint() {
        when(scannerSession.transportType()).thenReturn(TransportType.STREAMABLE_HTTP);
        when(scannerSession.resolvedEndpoint()).thenReturn("http://localhost:3001/mcp");
        when(proxy.port()).thenReturn(9999);
        stubRequest("localhost", 3001, "/mcp");
        when(request.withService(any(HttpService.class))).thenReturn(rewrittenRequest);

        RequestToBeSentAction result = handler.handleHttpRequestToBeSent(request);

        assertThat(result.request()).isSameAs(rewrittenRequest);
        verify(request).withService(any(HttpService.class));
    }

    /**
     * Well-known OAuth metadata probes (PRM / AS) sit on the same host as the MCP endpoint but on a
     * different path. They MUST pass through to Burp's normal HTTP delivery so the MCP OAuth
     * Metadata SSRF check can read the real upstream's response (status + body + WWW-Authenticate).
     * Previously the handler rewrote anything on host+port through the local proxy, which returned
     * 405 for GET and stripped headers — killing the check's three probes.
     */
    @Test
    void wellKnownOauthProtectedResourcePathPassesThroughUntouched() {
        when(scannerSession.transportType()).thenReturn(TransportType.STREAMABLE_HTTP);
        when(scannerSession.resolvedEndpoint()).thenReturn("http://127.0.0.1:8000/mcp");
        stubRequest("127.0.0.1", 8000, "/.well-known/oauth-protected-resource");

        RequestToBeSentAction result = handler.handleHttpRequestToBeSent(request);

        assertThat(result.request()).isSameAs(request);
        verify(request, never()).withService(any(HttpService.class));
        verify(annotations, never()).setNotes(any());
    }

    @Test
    void wellKnownOauthAuthorizationServerPathPassesThroughUntouched() {
        when(scannerSession.transportType()).thenReturn(TransportType.STREAMABLE_HTTP);
        when(scannerSession.resolvedEndpoint()).thenReturn("http://127.0.0.1:8000/mcp");
        stubRequest("127.0.0.1", 8000, "/.well-known/oauth-authorization-server");

        RequestToBeSentAction result = handler.handleHttpRequestToBeSent(request);

        assertThat(result.request()).isSameAs(request);
        verify(request, never()).withService(any(HttpService.class));
        verify(annotations, never()).setNotes(any());
    }

    /**
     * Burp Scanner's backup-file / path-walking probes (e.g. "/v1/mcp/authv2 (copy)",
     * "/v1/mcp/authv2:twciaf.css") differ in their path component from the MCP endpoint, so under
     * the path-match policy they pass through. Their previous interception was a defence against
     * upstreams that returned SSE for ALL paths on the host — operators whose servers do that should
     * scope Burp out of those paths via Target scope.
     */
    @Test
    void backupFileSuffixedPathPassesThroughUntouched() {
        when(scannerSession.transportType()).thenReturn(TransportType.STREAMABLE_HTTP);
        when(scannerSession.resolvedEndpoint()).thenReturn("https://mcp.example.com/v1/mcp/authv2");
        stubRequest("mcp.example.com", 443, "/v1/mcp/authv2%20(copy)");

        RequestToBeSentAction result = handler.handleHttpRequestToBeSent(request);

        assertThat(result.request()).isSameAs(request);
        verify(request, never()).withService(any(HttpService.class));
    }

    @Test
    void completelyDifferentPathOnSameHostPassesThroughUntouched() {
        when(scannerSession.transportType()).thenReturn(TransportType.STREAMABLE_HTTP);
        when(scannerSession.resolvedEndpoint()).thenReturn("https://mcp.example.com/v1/mcp/authv2");
        stubRequest("mcp.example.com", 443, "/v1/mcp/Copy%20of%20authv2");

        RequestToBeSentAction result = handler.handleHttpRequestToBeSent(request);

        assertThat(result.request()).isSameAs(request);
        verify(request, never()).withService(any(HttpService.class));
    }

    /**
     * Root-path regression: when the configured endpoint has no explicit path (e.g.
     * {@code https://mcp.example.com}), {@link URI#getPath()} returns the empty string but Burp
     * normalizes the request path to {@code "/"}. Without empty-path normalization the comparison
     * silently fails and every scan bypasses the local SSE proxy, so Burp tries to scan the
     * upstream's streaming response directly and aborts the audit.
     */
    @Test
    void proxyTargetMatchesRootPathEndpointAgainstSlashRequest() {
        when(scannerSession.transportType()).thenReturn(TransportType.STREAMABLE_HTTP);
        when(scannerSession.resolvedEndpoint()).thenReturn("https://mcp.example.com");
        when(proxy.port()).thenReturn(9999);
        stubRequest("mcp.example.com", 443, "/");
        when(request.withService(any(HttpService.class))).thenReturn(rewrittenRequest);

        RequestToBeSentAction result = handler.handleHttpRequestToBeSent(request);

        assertThat(result.request()).isSameAs(rewrittenRequest);
        verify(request).withService(any(HttpService.class));
    }

    @Test
    void proxyTargetMatchesRootPathEndpointWithTrailingSlash() {
        when(scannerSession.transportType()).thenReturn(TransportType.STREAMABLE_HTTP);
        when(scannerSession.resolvedEndpoint()).thenReturn("https://mcp.example.com/");
        when(proxy.port()).thenReturn(9999);
        stubRequest("mcp.example.com", 443, "/");
        when(request.withService(any(HttpService.class))).thenReturn(rewrittenRequest);

        RequestToBeSentAction result = handler.handleHttpRequestToBeSent(request);

        assertThat(result.request()).isSameAs(rewrittenRequest);
        verify(request).withService(any(HttpService.class));
    }

    @Test
    void mcpEndpointPathStillGetsRewritten() {
        when(scannerSession.transportType()).thenReturn(TransportType.STREAMABLE_HTTP);
        when(scannerSession.resolvedEndpoint()).thenReturn("http://127.0.0.1:8000/mcp");
        when(proxy.port()).thenReturn(9999);
        stubRequest("127.0.0.1", 8000, "/mcp");
        when(request.withService(any(HttpService.class))).thenReturn(rewrittenRequest);

        RequestToBeSentAction result = handler.handleHttpRequestToBeSent(request);

        assertThat(result.request()).isSameAs(rewrittenRequest);
        verify(request).withService(any(HttpService.class));
    }

    @Test
    void doesNotRewriteRequestToDifferentHost() {
        when(scannerSession.transportType()).thenReturn(TransportType.STREAMABLE_HTTP);
        when(scannerSession.resolvedEndpoint()).thenReturn("http://localhost:3001/mcp");
        when(request.httpService()).thenReturn(httpService);
        when(httpService.host()).thenReturn("other-host.com");
        when(request.annotations()).thenReturn(annotations);

        RequestToBeSentAction result = handler.handleHttpRequestToBeSent(request);

        assertThat(result.request()).isSameAs(request);
        verify(annotations, never()).setNotes(any());
    }

    @Test
    void doesNotRewriteWhenTransportIsNeitherSseNorStreamableHttp() {
        when(scannerSession.transportType()).thenReturn(null);
        when(request.annotations()).thenReturn(annotations);

        RequestToBeSentAction result = handler.handleHttpRequestToBeSent(request);

        assertThat(result.request()).isSameAs(request);
        verify(annotations, never()).setNotes(any());
    }

    @Test
    void doesNotRewriteWhenEndpointNotResolved() {
        when(scannerSession.transportType()).thenReturn(TransportType.STREAMABLE_HTTP);
        when(scannerSession.resolvedEndpoint()).thenReturn(null);

        RequestToBeSentAction result = handler.handleHttpRequestToBeSent(request);

        assertThat(result.request()).isSameAs(request);
    }

    @Test
    void passesResponseThrough() {
        ResponseReceivedAction result = handler.handleHttpResponseReceived(response);

        assertThat(result.response()).isSameAs(response);
    }

    @Test
    void observesResponseWhoseInitiatingRequestMatchesMcpEndpoint() {
        when(scannerSession.transportType()).thenReturn(TransportType.STREAMABLE_HTTP);
        when(scannerSession.resolvedEndpoint()).thenReturn("http://localhost:3001/mcp");
        stubInitiatingRequest("localhost", 3001, "/mcp");

        ResponseReceivedAction result = handler.handleHttpResponseReceived(response);

        assertThat(result.response()).isSameAs(response);
        verify(observer).observeResponse(response);
    }

    @Test
    void doesNotObserveResponseWhoseInitiatingRequestDoesNotMatch() {
        when(scannerSession.transportType()).thenReturn(TransportType.STREAMABLE_HTTP);
        when(scannerSession.resolvedEndpoint()).thenReturn("http://localhost:3001/mcp");
        stubInitiatingRequest("localhost", 3001, "/.well-known/oauth-protected-resource");

        ResponseReceivedAction result = handler.handleHttpResponseReceived(response);

        assertThat(result.response()).isSameAs(response);
        verify(observer, never()).observeResponse(any());
    }

    /**
     * Off-mode invariant: a matching response handed to the no-op observer must not throw and the
     * response path must never consult {@code response.toolSource()}. {@code proxyTarget} reads
     * transport/endpoint/host/path only — never tool source — so off-mode stays byte-identical to
     * the pre-seam pass-through. (The initiating {@link HttpRequest} carries no {@code toolSource()}
     * at all, so there is nothing to consult there.)
     */
    @Test
    void responsePath_doesNotConsultToolSource() {
        McpHttpHandler offModeHandler =
                new McpHttpHandler(scannerSession, proxy, new SwapAllMatchingTools(),
                        new NoopBurpTrafficObserver());
        when(scannerSession.transportType()).thenReturn(TransportType.STREAMABLE_HTTP);
        when(scannerSession.resolvedEndpoint()).thenReturn("http://localhost:3001/mcp");
        stubInitiatingRequest("localhost", 3001, "/mcp");

        offModeHandler.handleHttpResponseReceived(response);

        verify(response, never()).toolSource();
    }

    /**
     * Compatibility invariant: with {@link SwapAllMatchingTools} the handler must reach its swap
     * decision without ever consulting the request's {@code toolSource()}. All tool logic lives
     * inside the injected policy/observer; the handler stays tool-agnostic so off-mode is
     * byte-identical to today.
     */
    @Test
    void defaultPath_doesNotConsultToolSource() {
        when(scannerSession.transportType()).thenReturn(TransportType.STREAMABLE_HTTP);
        when(scannerSession.resolvedEndpoint()).thenReturn("http://localhost:3001/mcp");
        when(proxy.port()).thenReturn(9999);
        stubRequest("localhost", 3001, "/mcp");
        when(request.withService(any(HttpService.class))).thenReturn(rewrittenRequest);

        handler.handleHttpRequestToBeSent(request);

        verify(request, never()).toolSource();
    }

    /**
     * With {@link SwapAllMatchingTools} every matching request is swapped, so the observe branch is
     * never reached and the injected observer is never touched.
     */
    @Test
    void defaultObserver_neverObserves() {
        when(scannerSession.transportType()).thenReturn(TransportType.STREAMABLE_HTTP);
        when(scannerSession.resolvedEndpoint()).thenReturn("http://localhost:3001/mcp");
        when(proxy.port()).thenReturn(9999);
        stubRequest("localhost", 3001, "/mcp");
        when(request.withService(any(HttpService.class))).thenReturn(rewrittenRequest);

        RequestToBeSentAction result = handler.handleHttpRequestToBeSent(request);

        assertThat(result.request()).isSameAs(rewrittenRequest);
        verifyNoInteractions(observer);
    }

    /**
     * When the scanner-family policy is injected, a matching request from the scanner family
     * (Repeater here) is swapped to the loopback exactly as before, and the observer is not invoked.
     */
    @Test
    void scannerFamilyPolicy_swapsScannerFamilyRequest_andDoesNotObserve() {
        when(scannerSession.transportType()).thenReturn(TransportType.STREAMABLE_HTTP);
        when(scannerSession.resolvedEndpoint()).thenReturn("http://localhost:3001/mcp");
        when(proxy.port()).thenReturn(9999);
        stubRequest("localhost", 3001, "/mcp");
        when(request.withService(any(HttpService.class))).thenReturn(rewrittenRequest);
        stubToolSource(true);

        RequestToBeSentAction result = scannerFamilyHandler().handleHttpRequestToBeSent(request);

        assertThat(result.request()).isSameAs(rewrittenRequest);
        verify(request).withService(any(HttpService.class));
        verifyNoInteractions(observer);
    }

    /**
     * When the scanner-family policy is injected, a matching request from a non-scanner-family tool
     * (live Proxy traffic) is NOT swapped — it continues with the original request — and is handed to
     * the observer instead.
     */
    @Test
    void scannerFamilyPolicy_observesMatchingNonScannerFamilyRequest_withoutSwapping() {
        when(scannerSession.transportType()).thenReturn(TransportType.STREAMABLE_HTTP);
        when(scannerSession.resolvedEndpoint()).thenReturn("http://localhost:3001/mcp");
        stubRequest("localhost", 3001, "/mcp");
        stubToolSource(false);

        RequestToBeSentAction result = scannerFamilyHandler().handleHttpRequestToBeSent(request);

        assertThat(result.request()).isSameAs(request);
        verify(request, never()).withService(any(HttpService.class));
        verify(observer).observeRequest(request);
    }

    /**
     * When the scanner-family policy is injected, a non-matching request (different path) passes
     * through untouched and the observer is never consulted — only matching-but-not-swapped requests
     * are observed.
     */
    @Test
    void scannerFamilyPolicy_passesThroughNonMatchingRequest_withoutObserving() {
        when(scannerSession.transportType()).thenReturn(TransportType.STREAMABLE_HTTP);
        when(scannerSession.resolvedEndpoint()).thenReturn("http://localhost:3001/mcp");
        stubRequest("localhost", 3001, "/.well-known/oauth-protected-resource");

        RequestToBeSentAction result = scannerFamilyHandler().handleHttpRequestToBeSent(request);

        assertThat(result.request()).isSameAs(request);
        verify(request, never()).withService(any(HttpService.class));
        verifyNoInteractions(observer);
    }

    private McpHttpHandler scannerFamilyHandler() {
        return new McpHttpHandler(scannerSession, proxy, new ScannerFamilySwapPolicy(), observer);
    }

    private void stubToolSource(boolean isFromScannerFamily) {
        when(toolSource.isFromTool(ToolType.REPEATER, ToolType.SCANNER, ToolType.INTRUDER))
                .thenReturn(isFromScannerFamily);
        when(request.toolSource()).thenReturn(toolSource);
    }

    private void stubRequest(String host, int port, String pathWithoutQuery) {
        when(request.httpService()).thenReturn(httpService);
        when(httpService.host()).thenReturn(host);
        when(httpService.port()).thenReturn(port);
        when(request.pathWithoutQuery()).thenReturn(pathWithoutQuery);
        when(request.annotations()).thenReturn(annotations);
    }

    private void stubInitiatingRequest(String host, int port, String pathWithoutQuery) {
        when(response.initiatingRequest()).thenReturn(initiatingRequest);
        when(initiatingRequest.httpService()).thenReturn(httpService);
        when(httpService.host()).thenReturn(host);
        when(httpService.port()).thenReturn(port);
        when(initiatingRequest.pathWithoutQuery()).thenReturn(pathWithoutQuery);
    }
}
