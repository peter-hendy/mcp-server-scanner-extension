package com.mcpscanner.checks.shared.session;

import burp.api.montoya.http.HttpService;
import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.message.requests.HttpRequest;

final class HostHeaderFidelity {

    private static final String HOST = "Host";

    private HostHeaderFidelity() {
    }

    static boolean keptOutsideBurp(HttpRequest probe) {
        HttpService service = probe.httpService();
        return probe.headers().stream().filter(header -> HOST.equalsIgnoreCase(header.name()))
                .map(HttpHeader::value)
                .allMatch(host -> host.equalsIgnoreCase(service.host())
                        || host.equalsIgnoreCase(service.host() + ":" + service.port()));
    }
}
