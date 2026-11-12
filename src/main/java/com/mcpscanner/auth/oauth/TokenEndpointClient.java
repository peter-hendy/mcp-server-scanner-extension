package com.mcpscanner.auth.oauth;

import com.mcpscanner.auth.oauth.safety.FetchPurpose;
import com.mcpscanner.auth.oauth.safety.SuspiciousDestinationGate;
import com.nimbusds.oauth2.sdk.ParseException;
import com.nimbusds.oauth2.sdk.http.HTTPRequest;
import com.nimbusds.oauth2.sdk.http.ReadOnlyHTTPResponse;
import com.nimbusds.oauth2.sdk.util.JSONObjectUtils;
import com.mcpscanner.core.oauth.OAuthFlowException;
import com.mcpscanner.core.oauth.TokenExchange;
import com.mcpscanner.core.oauth.TokenRequest;
import com.mcpscanner.core.oauth.TokenResponse;

import java.io.IOException;
import java.net.URI;
import java.util.Objects;

public final class TokenEndpointClient implements TokenExchange {

    private static final String PURPOSE = "Token Endpoint";

    private final BurpHttpRequestSender sender;
    private final SuspiciousDestinationGate gate;

    public TokenEndpointClient(BurpHttpRequestSender sender, SuspiciousDestinationGate gate) {
        this.sender = Objects.requireNonNull(sender);
        this.gate = Objects.requireNonNull(gate);
    }

    @Override
    public TokenResponse exchange(TokenRequest request) {
        requireAllowedDestination(request);
        ReadOnlyHTTPResponse response = post(request);
        rejectRedirect(response);
        return parse(response);
    }

    private void requireAllowedDestination(TokenRequest request) {
        String resource = request.form().get("resource");
        FetchPurpose purpose = FetchPurpose.of(PURPOSE, resource == null ? null : URI.create(resource));
        if (gate.evaluate(request.endpoint(), purpose).isDenied()) {
            throw failure("Token endpoint destination denied");
        }
    }

    private ReadOnlyHTTPResponse post(TokenRequest request) {
        HTTPRequest httpRequest = new HTTPRequest(HTTPRequest.Method.POST, request.endpoint());
        request.headers().forEach(httpRequest::setHeader);
        httpRequest.setBody(request.body());
        try {
            return sender.send(OAuthHttpTimeouts.apply(httpRequest));
        } catch (IOException | RuntimeException transportFailure) {
            throw exchangeFailed();
        }
    }

    private static void rejectRedirect(ReadOnlyHTTPResponse response) {
        if (response.getStatusCode() / 100 == 3) {
            throw failure("Token endpoint redirect rejected");
        }
    }

    private static TokenResponse parse(ReadOnlyHTTPResponse response) {
        try {
            return new TokenResponse(response.getStatusCode(), JSONObjectUtils.parse(response.getBody()));
        } catch (ParseException | RuntimeException malformed) {
            throw exchangeFailed();
        }
    }

    private static OAuthFlowException exchangeFailed() {
        return failure("Token endpoint exchange failed");
    }

    private static OAuthFlowException failure(String message) {
        return new OAuthFlowException(OAuthFlowException.Reason.EXCHANGE_FAILED, message);
    }
}
