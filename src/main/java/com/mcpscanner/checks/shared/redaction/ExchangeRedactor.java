package com.mcpscanner.checks.shared.redaction;

import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import com.mcpscanner.auth.AuthHeaderNames;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class ExchangeRedactor {

    public static final String REDACTED = "[redacted by MCP scanner]";
    private static final int MINIMUM_SECRET_LENGTH = 4;
    private static final Set<String> FRAMING_HEADERS = Set.of("content-length", "transfer-encoding");
    private static final Pattern TOKEN_FIELDS = Pattern.compile(
            "(\"(?:access_token|refresh_token|id_token|client_secret)\"\\s*:\\s*\")[^\"]*(\")");

    private final List<String> secrets;

    public ExchangeRedactor(Set<String> secrets) {
        this.secrets = secrets.stream()
                .filter(secret -> secret != null && secret.length() >= MINIMUM_SECRET_LENGTH)
                .flatMap(ExchangeRedactor::encodings)
                .distinct()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .toList();
    }

    public HttpRequestResponse redact(HttpRequestResponse exchange) {
        return HttpRequestResponse.httpRequestResponse(redact(exchange.request()),
                exchange.response() == null ? null : redact(exchange.response()));
    }

    public HttpRequestResponse redact(HttpRequestResponse exchange, String responseBody) {
        return HttpRequestResponse.httpRequestResponse(redact(exchange.request()),
                rebuilt(exchange.response(), redact(responseBody)));
    }

    public String redact(String text) {
        String redacted = text == null ? "" : text;
        for (String secret : secrets) {
            redacted = redacted.replace(secret, REDACTED);
        }
        return TOKEN_FIELDS.matcher(redacted).replaceAll("$1" + REDACTED + "$2");
    }

    private static Stream<String> encodings(String secret) {
        return Stream.of(secret, URLEncoder.encode(secret, StandardCharsets.UTF_8), secret.replace("/", "\\/"));
    }

    private HttpRequest redact(HttpRequest request) {
        HttpRequest redacted = request.withBody(redact(request.bodyToString()));
        String authorization = request.headerValue(AuthHeaderNames.AUTHORIZATION);
        return authorization == null ? redacted
                : redacted.withHeader(AuthHeaderNames.AUTHORIZATION, redact(authorization));
    }

    private HttpResponse redact(HttpResponse response) {
        return rebuilt(response, redact(response.bodyToString()));
    }

    private HttpResponse rebuilt(HttpResponse response, String body) {
        String headers = response.headers().stream()
                .filter(header -> !FRAMING_HEADERS.contains(header.name().toLowerCase(Locale.ROOT)))
                .map(HttpHeader::toString)
                .map(this::redact)
                .collect(Collectors.joining("\r\n"));
        return HttpResponse.httpResponse("HTTP/1.1 " + response.statusCode() + "\r\n" + headers + "\r\n\r\n" + body);
    }
}
