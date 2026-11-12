package com.mcpscanner.checks.catalog.oauthauthorizationconsent;

import com.mcpscanner.checks.shared.oauth.Pkce;

record IssuedCode(String value, Pkce pkce) {
}
