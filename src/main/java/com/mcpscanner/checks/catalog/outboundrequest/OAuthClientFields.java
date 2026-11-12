package com.mcpscanner.checks.catalog.outboundrequest;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.checks.shared.jsonrpc.JsonRpcMessages;

import java.util.Set;
import java.util.regex.Pattern;

final class OAuthClientFields {

    private static final Set<String> FETCHED_URL_FIELDS = Set.of("client_uri", "logo_uri", "policy_uri", "tos_uri",
            "jwks_uri", "sector_identifier_uri", "initiate_login_uri", "request_uri", "client_id");
    private static final Pattern AUTHORIZATION_QUERY = Pattern.compile("[?&]response_type=");

    private OAuthClientFields() {}

    static boolean isFetchedUrlField(HttpRequest request, String fieldName) {
        return FETCHED_URL_FIELDS.contains(fieldName) && isClientRequest(request);
    }

    private static boolean isClientRequest(HttpRequest request) {
        return AUTHORIZATION_QUERY.matcher(request.url()).find()
                || JsonRpcMessages.parsed(request.body().getBytes())
                        .filter(body -> body.has("redirect_uris"))
                        .isPresent();
    }
}
