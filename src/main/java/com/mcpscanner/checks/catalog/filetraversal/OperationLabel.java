package com.mcpscanner.checks.catalog.filetraversal;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.mcpscanner.checks.shared.jsonrpc.JsonRpcEnvelope;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class OperationLabel {

    private OperationLabel() {}

    private static final Pattern SCHEME = Pattern.compile("^[A-Za-z][A-Za-z0-9+.-]*:(?://)?");

    static String of(HttpRequest request) {
        return JsonRpcEnvelope.of(request).root().map(OperationLabel::of).orElse("unparseable request");
    }

    private static String of(JsonNode body) {
        String method = body.path("method").asText("unknown method");
        JsonNode params = body.path("params");
        String subject = params.path("name").isTextual() ? params.path("name").asText()
                : uriScheme(params.path("uri").asText(""));
        return subject.isEmpty() ? method : method + " " + subject;
    }

    private static String uriScheme(String uri) {
        Matcher matcher = SCHEME.matcher(uri);
        return matcher.find() ? matcher.group() : "";
    }
}
