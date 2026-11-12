package com.mcpscanner.checks.shared.stream;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.checks.shared.probe.ProbeChannel;
import com.mcpscanner.checks.shared.session.ProbeEndpoint;
import com.mcpscanner.checks.shared.session.ProbeSessions;
import com.mcpscanner.checks.shared.session.SessionCleanup;
import com.mcpscanner.core.connection.HttpProbeSession;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;

public final class FreshSessionStreams implements EventStreams {

    private final ProbeSessions sessions;
    private final StreamOpener opener;

    public FreshSessionStreams(ProbeSessions sessions, StreamOpener opener) {
        this.sessions = sessions;
        this.opener = opener;
    }

    @Override
    public Optional<StreamOpening> open(HttpRequestResponse base, Http http, ProbeChannel budget,
                                        UnaryOperator<HttpRequest> shaping) {
        List<HttpRequestResponse> handshake = new ArrayList<>();
        return sessions.endpoint(base, http, exchange -> {
            handshake.add(exchange);
            budget.record(exchange);
        }).map(endpoint -> new Attempt(base.request(), shaping, budget, handshake, opener.reportingTo(http)).run(endpoint));
    }

    private final class Attempt {

        private final HttpRequest seed;
        private final UnaryOperator<HttpRequest> shaping;
        private final ProbeChannel budget;
        private final List<HttpRequestResponse> handshake;
        private final StreamOpener reportedOpener;

        Attempt(HttpRequest seed, UnaryOperator<HttpRequest> shaping, ProbeChannel budget,
                List<HttpRequestResponse> handshake, StreamOpener reportedOpener) {
            this.seed = seed;
            this.shaping = shaping;
            this.budget = budget;
            this.handshake = handshake;
            this.reportedOpener = reportedOpener;
        }

        StreamOpening run(ProbeEndpoint endpoint) {
            if (budget.exhausted()) {
                return StreamOpening.unanswered(get(Optional.empty()));
            }
            Optional<HttpProbeSession> session = handshakeWith(endpoint);
            try {
                if (session.isEmpty() && !serverAnswered() || budget.exhausted()) {
                    return StreamOpening.unanswered(get(Optional.empty()));
                }
                StreamOpening opening = reportedOpener.open(get(session));
                budget.record(opening.exchange());
                return opening;
            } finally {
                session.ifPresent(SessionCleanup::delete);
            }
        }

        private HttpRequest get(Optional<HttpProbeSession> session) {
            return shaping.apply(session.map(opened -> StreamGetRequest.in(seed, opened))
                    .orElseGet(() -> StreamGetRequest.from(seed, Optional.empty())));
        }

        private Optional<HttpProbeSession> handshakeWith(ProbeEndpoint endpoint) {
            try {
                return Optional.of(endpoint.open());
            } catch (RuntimeException refusedOrUnanswered) {
                return Optional.empty();
            }
        }

        private boolean serverAnswered() {
            return !handshake.isEmpty() && handshake.get(handshake.size() - 1).response() != null;
        }
    }
}
