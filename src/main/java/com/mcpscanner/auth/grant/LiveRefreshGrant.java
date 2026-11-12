package com.mcpscanner.auth.grant;

public interface LiveRefreshGrant {

    RefreshGrant grant();

    void adopt(IssuedTokens tokens);
}
