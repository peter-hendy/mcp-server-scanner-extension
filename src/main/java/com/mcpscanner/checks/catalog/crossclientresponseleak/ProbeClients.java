package com.mcpscanner.checks.catalog.crossclientresponseleak;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.message.HttpRequestResponse;
import com.mcpscanner.checks.api.CheckLog;
import com.mcpscanner.checks.shared.session.ProbeSessions;

import java.util.ArrayList;
import java.util.List;

final class ProbeClients {

    private final ProbeSessions sessions;
    private final CheckLog log;

    ProbeClients(ProbeSessions sessions, CheckLog log) {
        this.sessions = sessions;
        this.log = log;
    }

    Opening<ProbeClient> open(HttpRequestResponse base, Http http) {
        return ProbeClient.open(sessions, base, http, log);
    }

    Opening<ProbeClient> openAnother(HttpRequestResponse base, Http http) {
        return open(base, http).laterOpening();
    }

    Opening<List<ProbeClient>> openAll(HttpRequestResponse base, Http http, int count) {
        List<ProbeClient> opened = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            Opening<ProbeClient> client = openAnother(base, http);
            if (client.opened().isEmpty()) {
                opened.forEach(ProbeClient::close);
                return Opening.refused(client.refusal());
            }
            opened.add(client.opened().get());
        }
        return Opening.of(opened);
    }
}
