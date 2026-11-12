package com.mcpscanner.checks.catalog.oauthredirectpolicy;

import com.mcpscanner.checks.shared.oauth.ProbeCallback;

import java.net.URI;
import java.util.UUID;

final class CallbackUris {

    private static final String CLIENT_HOST = "client.example.com";
    private static final String UNRELATED_HOST = "example.org";

    private CallbackUris() {
    }

    static URI loopbackRegistered() {
        return ProbeCallback.loopback("scancheck-registered-" + nonce());
    }

    static URI loopbackUnregistered() {
        return ProbeCallback.loopback("scancheck-unregistered-" + nonce());
    }

    static URI httpsControl() {
        return URI.create("https://" + CLIENT_HOST + "/scancheck-control-" + nonce());
    }

    static URI httpDowngradeOf(URI registered) {
        return URI.create("http://" + registered.getRawAuthority() + registered.getRawPath());
    }

    static URI traversalBeneath(URI registered) {
        return URI.create(registered + "/../scancheck-bypass-" + nonce());
    }

    static WildcardCallback wildcard() {
        String path = "/scancheck-wildcard-" + nonce();
        return new WildcardCallback("https://*." + CLIENT_HOST + path,
                URI.create("https://scancheck-" + nonce() + "." + CLIENT_HOST + path),
                URI.create("https://scancheck-" + nonce() + "." + UNRELATED_HOST + path),
                URI.create("https://scancheck-" + nonce() + "." + CLIENT_HOST + "/scancheck-unlisted-" + nonce()));
    }

    private static String nonce() {
        return UUID.randomUUID().toString();
    }

    record WildcardCallback(String pattern, URI matchingHost, URI unmatchedHost, URI unmatchedPath) {
    }
}
