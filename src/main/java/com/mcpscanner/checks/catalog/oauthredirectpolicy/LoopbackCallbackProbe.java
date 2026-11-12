package com.mcpscanner.checks.catalog.oauthredirectpolicy;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

final class LoopbackCallbackProbe implements RedirectProbe {

    private static final String SHAPE = "unregistered path on the registered loopback host and port";

    @Override
    public List<RedirectFinding> run(ProbeContext context) {
        return context.clients().loopback()
                .map(client -> probeEveryVariant(context, client, CallbackUris.loopbackUnregistered()))
                .orElse(List.of());
    }

    private static List<RedirectFinding> probeEveryVariant(ProbeContext context, OwnedClient client, URI candidate) {
        return Arrays.stream(AuthorizationVariant.values())
                .map(variant -> context.delivery().probe(client, DeliveryCase.of(SHAPE, candidate, variant)))
                .flatMap(Optional::stream)
                .toList();
    }
}
