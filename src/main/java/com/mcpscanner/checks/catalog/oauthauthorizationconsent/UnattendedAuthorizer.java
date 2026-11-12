package com.mcpscanner.checks.catalog.oauthauthorizationconsent;

import com.mcpscanner.checks.shared.oauth.AuthorizationParameters;
import com.mcpscanner.checks.shared.oauth.AuthorizationServer;
import com.mcpscanner.checks.shared.oauth.OAuthHttp;
import com.mcpscanner.checks.shared.oauth.CallbackResponse;
import com.mcpscanner.checks.shared.oauth.Pkce;
import com.mcpscanner.checks.shared.oauth.ProbeCallback;
import com.mcpscanner.checks.shared.oauth.RedirectTrail;
import com.mcpscanner.checks.shared.oauth.SameOriginRedirects;

import java.net.URI;
import java.util.UUID;

final class UnattendedAuthorizer {

    static final int MAX_REDIRECTS = 5;

    private final SameOriginRedirects redirects;
    private final AuthorizationServer server;

    UnattendedAuthorizer(OAuthHttp http, AuthorizationServer server) {
        this.redirects = SameOriginRedirects.carryingCookies(http, MAX_REDIRECTS);
        this.server = server;
    }

    UnattendedAuthorization authorize(ProbeClient client) {
        Pkce pkce = Pkce.generate();
        String state = UUID.randomUUID().toString();
        URI request = AuthorizationParameters.withQuery(server.authorizationEndpoint(),
                AuthorizationParameters.of(server, "code", client.clientId(), client.callback(), state, pkce));
        return redirects.follow(request, client::isCallback)
                .map(trail -> authorization(trail, client, state, pkce))
                .orElseGet(UnattendedAuthorization::unanswered);
    }

    private static UnattendedAuthorization authorization(RedirectTrail trail, ProbeClient client, String state,
                                                         Pkce pkce) {
        return new UnattendedAuthorization(
                trail.stoppedAt().flatMap(location -> ProbeCallback.response(client.callback(), location, state))
                        .flatMap(CallbackResponse::code)
                        .map(code -> new IssuedCode(code, pkce)),
                trail.exchanges(),
                trail.sessionCreatedBy(),
                trail.hopLimitReached());
    }
}
