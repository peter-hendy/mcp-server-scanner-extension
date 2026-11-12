package com.mcpscanner.checks.catalog.downstreamrouteescape;

import burp.api.montoya.http.message.HttpRequestResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;

final class RouteDifferential {

    private final ReplySignature signatures;
    private final RejectionWording rejections;

    RouteDifferential(ReplySignature signatures, RejectionWording rejections) {
        this.signatures = signatures;
        this.rejections = rejections;
    }

    Optional<RouteDifference> run(RouteProbe probe, ValueSender sender) {
        List<HttpRequestResponse> evidence = new ArrayList<>();
        Optional<String> traversal = agreeing(probe.traversals(), sender, evidence, this::traversalSignature);
        if (traversal.isEmpty()) {
            return Optional.empty();
        }
        Optional<String> control = agreeing(probe.controls(), sender, evidence, this::anySignature);
        if (control.isEmpty() || control.equals(traversal)
                || !contrastsDiffer(probe.contrasts(), traversal.get(), sender, evidence)) {
            return Optional.empty();
        }
        boolean repeatable = repeats(probe.traversals().get(0), traversal.get(), sender, evidence,
                this::traversalSignature)
                && repeats(probe.controls().get(0), control.get(), sender, evidence, this::anySignature);
        return repeatable ? Optional.of(new RouteDifference(probe, evidence)) : Optional.empty();
    }

    private static Optional<String> agreeing(List<String> values, ValueSender sender,
                                             List<HttpRequestResponse> evidence,
                                             BiFunction<HttpRequestResponse, String, Optional<String>> signature) {
        Optional<String> agreed = Optional.empty();
        for (String value : values) {
            HttpRequestResponse exchange = sender.send(value);
            evidence.add(exchange);
            Optional<String> observed = signature.apply(exchange, value);
            if (observed.isEmpty() || agreed.isPresent() && !agreed.equals(observed)) {
                return Optional.empty();
            }
            agreed = observed;
        }
        return agreed;
    }

    private boolean contrastsDiffer(List<String> contrasts, String traversal, ValueSender sender,
                                    List<HttpRequestResponse> evidence) {
        for (String contrast : contrasts) {
            HttpRequestResponse exchange = sender.send(contrast);
            evidence.add(exchange);
            if (signatures.of(exchange, contrast).equals(traversal)) {
                return false;
            }
        }
        return true;
    }

    private static boolean repeats(String value, String expected, ValueSender sender,
                                   List<HttpRequestResponse> evidence,
                                   BiFunction<HttpRequestResponse, String, Optional<String>> signature) {
        HttpRequestResponse exchange = sender.send(value);
        evidence.add(exchange);
        return signature.apply(exchange, value).filter(expected::equals).isPresent();
    }

    private Optional<String> traversalSignature(HttpRequestResponse exchange, String value) {
        return signatures.ofSuccess(exchange, value).filter(signature -> !rejections.statedIn(signature));
    }

    private Optional<String> anySignature(HttpRequestResponse exchange, String value) {
        return Optional.of(signatures.of(exchange, value));
    }
}
