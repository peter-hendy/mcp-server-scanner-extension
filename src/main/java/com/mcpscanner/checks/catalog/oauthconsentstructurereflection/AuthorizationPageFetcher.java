package com.mcpscanner.checks.catalog.oauthconsentstructurereflection;

import com.mcpscanner.checks.shared.oauth.OAuthHttp;
import com.mcpscanner.checks.shared.oauth.SameOriginRedirects;

import java.net.URI;
import java.util.Optional;

final class AuthorizationPageFetcher {

    static final int MAX_REDIRECTS = 3;

    private final SameOriginRedirects redirects;

    AuthorizationPageFetcher(OAuthHttp http) {
        this.redirects = SameOriginRedirects.withoutCookies(http, MAX_REDIRECTS);
    }

    Optional<AuthorizationPage> fetch(URI start) {
        return redirects.follow(start, location -> false)
                .map(trail -> new AuthorizationPage(trail.lastRequested(), trail.exchanges()));
    }
}
