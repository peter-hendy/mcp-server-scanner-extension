package com.mcpscanner.checks.catalog.sessionlifecycle;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.RedirectionMode;
import burp.api.montoya.http.RequestOptions;
import burp.api.montoya.http.message.HttpRequestResponse;
import com.mcpscanner.checks.api.CheckLog;

final class LiveSessionPing {

    private final PingRequests pings;
    private final CheckLog log;

    LiveSessionPing(PingRequests pings, CheckLog log) {
        this.pings = pings;
        this.log = log;
    }

    void confirm(HttpRequestResponse base, Http http) {
        try {
            report(http.sendRequest(base.request().withBody(pings.liveBody()),
                    RequestOptions.requestOptions().withRedirectionMode(RedirectionMode.NEVER)));
        } catch (RuntimeException failed) {
            log.warn("the scanner's live session could not be pinged after the probe sessions closed: " + failed
                    + "; reconnect before scanning further");
        }
    }

    private void report(HttpRequestResponse exchange) {
        if (PingReplies.answered(exchange)) {
            log.info("the scanner's live session still answers ping after the probe sessions closed");
        } else {
            log.warn("the scanner's live session did not answer ping after the probe sessions closed ("
                    + PingReplies.statusOf(exchange) + "); reconnect before scanning further");
        }
    }
}
