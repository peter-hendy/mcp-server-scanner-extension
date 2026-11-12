package com.mcpscanner.checks.catalog.oauthauthorizationconsent;

import com.mcpscanner.checks.shared.oauth.IssuedToken;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

final class TokenLedger {

    private final List<IssuedToken> issued = new CopyOnWriteArrayList<>();

    void record(Redemption redemption) {
        redemption.accessToken().map(IssuedToken::access).ifPresent(issued::add);
        redemption.refreshToken().map(IssuedToken::refresh).ifPresent(issued::add);
    }

    List<IssuedToken> issued() {
        return List.copyOf(issued);
    }
}
