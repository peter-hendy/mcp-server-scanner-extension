package com.mcpscanner.checks.catalog.crossclientresponseleak;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.message.HttpRequestResponse;
import com.mcpscanner.checks.api.CheckLog;
import com.mcpscanner.checks.catalog.crossclientresponseleak.TrialOutcome.Verdict;
import com.mcpscanner.checks.shared.session.ProbeSessions;
import com.mcpscanner.mcp.McpMethods;
import com.mcpscanner.core.connection.HttpProbeSession;
import com.mcpscanner.core.protocol.McpFlow;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

final class ProbeClient implements AutoCloseable {

    private final HttpProbeSession session;
    private final AtomicReference<HttpRequestResponse> lastExchange;
    private final CheckLog log;

    private ProbeClient(HttpProbeSession session, AtomicReference<HttpRequestResponse> lastExchange, CheckLog log) {
        this.session = session;
        this.lastExchange = lastExchange;
        this.log = log;
    }

    static Opening<ProbeClient> open(ProbeSessions sessions, HttpRequestResponse base, Http http, CheckLog log) {
        AtomicReference<HttpRequestResponse> lastExchange = new AtomicReference<>();
        try {
            Optional<ProbeClient> client = sessions.openHttp(base, http, lastExchange::set)
                    .map(session -> new ProbeClient(session, lastExchange, log));
            if (client.isEmpty()) {
                log.info("probe session not opened: the base URL is a legacy SSE message endpoint");
            }
            return client.map(Opening::of).orElseGet(() -> Opening.refused(Verdict.NO_PROBE_SESSION));
        } catch (RuntimeException refused) {
            log.info("probe session refused: " + describe(refused));
            return Opening.refused(HandshakeRefusal.verdict(base, lastExchange.get()));
        }
    }

    ClientReply call(EchoableCall call, String argument, String marker, long requestId) {
        lastExchange.set(null);
        return send(call.paramsWith(argument, marker), requestId)
                .map(response -> new ClientReply(marker, response.status(), bodyOf(response),
                        Optional.ofNullable(lastExchange.get())))
                .orElseGet(() -> ClientReply.unanswered(marker, Optional.ofNullable(lastExchange.get())));
    }

    @Override
    public void close() {
        try {
            session.close();
        } catch (RuntimeException failedTermination) {
            log.info("probe session DELETE failed: " + describe(failedTermination));
        }
    }

    private Optional<McpFlow.HttpResponse> send(String params, long requestId) {
        try {
            return Optional.ofNullable(session.send(
                    session.session().operation(McpMethods.TOOLS_CALL, params, requestId)));
        } catch (RuntimeException unanswered) {
            log.info("probe call unanswered: " + describe(unanswered));
            return Optional.empty();
        }
    }

    private static String bodyOf(McpFlow.HttpResponse response) {
        return response.body() == null ? "" : response.body();
    }

    private static String describe(RuntimeException failure) {
        return failure.getClass().getSimpleName() + ": " + failure.getMessage();
    }
}
