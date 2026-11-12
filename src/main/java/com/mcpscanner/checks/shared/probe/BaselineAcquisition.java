package com.mcpscanner.checks.shared.probe;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.checks.shared.auth.oracle.BaselineEquivalenceOracle;
import com.mcpscanner.checks.shared.session.SessionHeaders;
import com.mcpscanner.mcp.McpHeaders;

public final class BaselineAcquisition {

    private final AuthenticatedBaseline authenticatedBaseline;
    private final ProbeChannel channel;
    private final IndependentSessionSender sender;
    private final BaselineEquivalenceOracle oracle;

    public BaselineAcquisition(AuthenticatedBaseline authenticatedBaseline, ProbeChannel channel,
                        IndependentSessionSender sender, BaselineEquivalenceOracle oracle) {
        this.authenticatedBaseline = authenticatedBaseline;
        this.channel = channel;
        this.sender = sender;
        this.oracle = oracle;
    }

    public HttpRequestResponse acquire(HttpRequestResponse base) {
        return successful(base) ? base : acquireFresh(base.request());
    }

    public HttpRequestResponse acquireFresh(HttpRequest request) {
        HttpRequest authenticated = authenticatedBaseline.requestFor(request);
        HttpRequestResponse resent = channel.send(authenticated);
        if (successful(resent) || SessionHeaders.carriedBy(authenticated).isEmpty()) {
            return resent;
        }
        return sender.send(authenticated.withRemovedHeader(McpHeaders.SESSION_ID)).last();
    }

    private boolean successful(HttpRequestResponse exchange) {
        return oracle.successful(exchange);
    }
}
