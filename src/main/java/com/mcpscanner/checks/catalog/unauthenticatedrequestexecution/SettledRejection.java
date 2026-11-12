package com.mcpscanner.checks.catalog.unauthenticatedrequestexecution;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.responses.HttpResponse;
import com.mcpscanner.checks.shared.auth.AuthVariant;
import com.mcpscanner.checks.shared.auth.VariantFamily;
import com.mcpscanner.checks.shared.probe.ProbeExchange;

import java.util.List;
import java.util.Objects;
import java.util.Set;

final class SettledRejection {

    private static final Set<Integer> ABSENT_ROUTE_STATUSES = Set.of(404, 405);
    private static final String JSONRPC_MEMBER = "\"jsonrpc\"";

    boolean settles(AuthVariant variant, ProbeExchange candidate, List<ProbeExchange> refusedControls) {
        HttpResponse response = answerOf(candidate);
        if (response == null) {
            return false;
        }
        return refusedControls.stream().map(SettledRejection::answerOf).filter(Objects::nonNull)
                .anyMatch(control -> sameAnswer(response, control))
                || variant.family() == VariantFamily.PATH && isAbsentRoute(response);
    }

    private static HttpResponse answerOf(ProbeExchange probe) {
        HttpRequestResponse exchange = probe.last();
        return probe.conclusive() && exchange != null ? exchange.response() : null;
    }

    private static boolean sameAnswer(HttpResponse candidate, HttpResponse control) {
        return candidate.statusCode() == control.statusCode()
                && Objects.equals(candidate.bodyToString(), control.bodyToString());
    }

    private static boolean isAbsentRoute(HttpResponse response) {
        String body = response.bodyToString();
        return ABSENT_ROUTE_STATUSES.contains((int) response.statusCode())
                && (body == null || !body.contains(JSONRPC_MEMBER));
    }
}
