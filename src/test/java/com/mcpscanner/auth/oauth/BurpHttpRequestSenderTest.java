package com.mcpscanner.auth.oauth;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.RedirectionMode;
import burp.api.montoya.http.RequestOptions;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.core.ByteArray;
import com.mcpscanner.mcp.BoundedBodyReader;
import com.mcpscanner.testutil.MontoyaTestFactory;
import com.mcpscanner.testutil.RecordingBurpRequest;
import com.nimbusds.oauth2.sdk.http.HTTPRequest;
import com.nimbusds.oauth2.sdk.http.HTTPResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static com.mcpscanner.testutil.RecordingRequestOptions.recorded;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link BurpHttpRequestSender}: verifies the Nimbus
 * {@code ReadOnlyHTTPRequest} ↔ Montoya {@code HttpRequest}/{@code HttpResponse}
 * mapping in both directions, the transport-failure ({@code response()==null})
 * contract, the non-2xx passthrough, and the read-timeout → {@code withResponseTimeout}
 * mapping.
 */
class BurpHttpRequestSenderTest {

    @BeforeEach
    void installFactory() {
        MontoyaTestFactory.install();
    }

    @Test
    void getRequestRoundTripsMethodUrlAndHeadersAndMapsResponseBack() throws Exception {
        Http http = mock(Http.class);
        ArgumentCaptor<HttpRequest> sent = ArgumentCaptor.forClass(HttpRequest.class);
        when(http.sendRequest(sent.capture(), any(RequestOptions.class)))
                .thenAnswer(inv -> responseOf(inv.getArgument(0), 200, "OK",
                        "{\"issuer\":\"https://as.example.com\"}",
                        "Content-Type", "application/json"));

        BurpHttpRequestSender sender = new BurpHttpRequestSender(http);

        HTTPRequest request = new HTTPRequest(HTTPRequest.Method.GET,
                new URL("https://as.example.com/.well-known/oauth-authorization-server"));
        request.setHeader("Accept", "application/json");
        request.setReadTimeout(10_000);

        HTTPResponse response = (HTTPResponse) sender.send(request);

        HttpRequest captured = sent.getValue();
        assertThat(captured.method()).isEqualTo("GET");
        assertThat(captured.url())
                .isEqualTo("https://as.example.com/.well-known/oauth-authorization-server");
        assertThat(captured.headerValue("Accept")).isEqualTo("application/json");

        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getBody()).contains("https://as.example.com");
        assertThat(response.getHeaderValue("Content-Type")).isEqualTo("application/json");
    }

    @Test
    void postRequestRoundTripsBodyAndContentTypeAndMultiValuedHeader() throws Exception {
        RecordingBurpRequest recorder = new RecordingBurpRequest();

        Http http = mock(Http.class);
        when(http.sendRequest(any(HttpRequest.class), any(RequestOptions.class)))
                .thenAnswer(inv -> HttpRequestResponse.httpRequestResponse(
                        inv.getArgument(0), multiValuedResponse()));

        BurpHttpRequestSender sender = new BurpHttpRequestSender(http, recorder::startFrom);

        HTTPRequest request = new HTTPRequest(HTTPRequest.Method.POST,
                new URL("https://as.example.com/token"));
        request.setHeader("Content-Type", "application/x-www-form-urlencoded");
        // A multi-valued header (two values under the same name) must survive forward.
        request.setHeader("X-Multi", "one", "two");
        request.setBody("grant_type=refresh_token&refresh_token=abc");
        request.setReadTimeout(10_000);

        HTTPResponse response = (HTTPResponse) sender.send(request);

        assertThat(recorder.method()).isEqualTo("POST");
        assertThat(recorder.bodyBytes())
                .isEqualTo("grant_type=refresh_token&refresh_token=abc".getBytes(StandardCharsets.UTF_8));
        assertThat(recorder.addedHeaders()).contains(
                "Content-Type: application/x-www-form-urlencoded", "X-Multi: one", "X-Multi: two");

        // Multi-valued response header must survive back into the Nimbus response.
        assertThat(response.getHeaderMap().get("Set-Cookie")).containsExactly("a=1", "b=2");
    }

    @Test
    void nonSuccessStatusIsReturnedNotThrownWithBodyIntact() throws Exception {
        Http http = mock(Http.class);
        when(http.sendRequest(any(HttpRequest.class), any(RequestOptions.class)))
                .thenAnswer(inv -> responseOf(inv.getArgument(0), 401, "Unauthorized",
                        "{\"error\":\"invalid_token\"}", "Content-Type", "application/json"));

        BurpHttpRequestSender sender = new BurpHttpRequestSender(http);

        HTTPRequest request = new HTTPRequest(HTTPRequest.Method.POST,
                new URL("https://as.example.com/register"));
        request.setReadTimeout(10_000);

        HTTPResponse response = (HTTPResponse) sender.send(request);

        assertThat(response.getStatusCode()).isEqualTo(401);
        assertThat(response.getBody()).contains("invalid_token");
    }

    @Test
    void oversizedResponseThrowsBeforeConvertingBodyToText() throws Exception {
        HttpResponse upstream = mock(HttpResponse.class);
        ByteArray body = mock(ByteArray.class);
        when(body.length()).thenReturn(BoundedBodyReader.MAX_RESPONSE_BYTES + 1);
        when(upstream.body()).thenReturn(body);
        Http http = mock(Http.class);
        when(http.sendRequest(any(HttpRequest.class), any(RequestOptions.class)))
                .thenAnswer(inv -> HttpRequestResponse.httpRequestResponse(inv.getArgument(0), upstream));
        HTTPRequest request = new HTTPRequest(HTTPRequest.Method.GET,
                new URL("https://as.example.com/client.json"));

        assertThatThrownBy(() -> new BurpHttpRequestSender(http).send(request))
                .isInstanceOf(IOException.class)
                .hasMessageContaining(Integer.toString(BoundedBodyReader.MAX_RESPONSE_BYTES));
        verify(upstream, never()).bodyToString();
    }

    @Test
    void responseAtSizeLimitPreservesStatusHeadersAndBody() throws Exception {
        String body = "x".repeat(BoundedBodyReader.MAX_RESPONSE_BYTES);
        Http http = mock(Http.class);
        when(http.sendRequest(any(HttpRequest.class), any(RequestOptions.class)))
                .thenAnswer(inv -> responseOf(inv.getArgument(0), 401, "Unauthorized", body,
                        "Content-Type", "application/json"));
        HTTPRequest request = new HTTPRequest(HTTPRequest.Method.GET,
                new URL("https://as.example.com/client.json"));

        HTTPResponse response = (HTTPResponse) new BurpHttpRequestSender(http).send(request);

        assertThat(response.getStatusCode()).isEqualTo(401);
        assertThat(response.getHeaderValue("Content-Type")).isEqualTo("application/json");
        assertThat(response.getBody()).isEqualTo(body);
    }

    @Test
    void nullResponseThrowsIOException() {
        Http http = mock(Http.class);
        when(http.sendRequest(any(HttpRequest.class), any(RequestOptions.class)))
                .thenAnswer(inv -> HttpRequestResponse.httpRequestResponse(inv.getArgument(0), null));

        BurpHttpRequestSender sender = new BurpHttpRequestSender(http);

        assertThatThrownBy(() -> {
            HTTPRequest request = new HTTPRequest(HTTPRequest.Method.GET,
                    new URL("https://as.example.com/.well-known/oauth-authorization-server"));
            request.setReadTimeout(10_000);
            sender.send(request);
        }).isInstanceOf(IOException.class);
    }

    @Test
    void confidentialTokenPostCannotFollowRedirectOutsideDestinationGate() throws Exception {
        Http http = mock(Http.class);
        when(http.sendRequest(any(HttpRequest.class), any(RequestOptions.class)))
                .thenAnswer(inv -> responseOf(inv.getArgument(0), 307, "Temporary Redirect", "{}",
                        "Location", "https://127.0.0.1/private"));
        var request = new HTTPRequest(HTTPRequest.Method.POST, new URL("https://as.example.com/token"));
        request.setHeader("Authorization", "Basic encoded-secret");
        request.setBody("grant_type=client_credentials");

        var response = new BurpHttpRequestSender(http).send(request);

        var options = ArgumentCaptor.forClass(RequestOptions.class);
        verify(http).sendRequest(any(HttpRequest.class), options.capture());
        assertThat(recorded(options.getValue()).redirectionMode()).contains(RedirectionMode.NEVER);
        assertThat(response.getStatusCode()).isEqualTo(307);
    }

    @Test
    void readTimeoutMapsToWithResponseTimeout() throws Exception {
        Http http = mock(Http.class);
        when(http.sendRequest(any(HttpRequest.class), any(RequestOptions.class)))
                .thenAnswer(inv -> responseOf(inv.getArgument(0), 200, "OK", "{}",
                        "Content-Type", "application/json"));

        BurpHttpRequestSender sender = new BurpHttpRequestSender(http);

        HTTPRequest request = new HTTPRequest(HTTPRequest.Method.GET,
                new URL("https://as.example.com/.well-known/oauth-authorization-server"));
        request.setReadTimeout(7_500);

        sender.send(request);

        ArgumentCaptor<RequestOptions> opts = ArgumentCaptor.forClass(RequestOptions.class);
        verify(http).sendRequest(any(HttpRequest.class), opts.capture());
        assertThat(recorded(opts.getValue()).responseTimeout()).contains(Duration.ofMillis(7_500));
        assertThat(recorded(opts.getValue()).redirectionMode()).contains(RedirectionMode.NEVER);
    }

    private static HttpRequestResponse responseOf(HttpRequest request, int status, String message,
                                                  String body, String... headerPairs) {
        StringBuilder raw = new StringBuilder("HTTP/1.1 ").append(status).append(' ')
                .append(message).append("\r\n");
        for (int i = 0; i + 1 < headerPairs.length; i += 2) {
            raw.append(headerPairs[i]).append(": ").append(headerPairs[i + 1]).append("\r\n");
        }
        raw.append("\r\n").append(body);
        return HttpRequestResponse.httpRequestResponse(request, HttpResponse.httpResponse(raw.toString()));
    }

    private static HttpResponse multiValuedResponse() {
        String raw = "HTTP/1.1 200 OK\r\n"
                + "Content-Type: application/json\r\n"
                + "Set-Cookie: a=1\r\n"
                + "Set-Cookie: b=2\r\n"
                + "\r\n"
                + "{}";
        return HttpResponse.httpResponse(raw);
    }
}
