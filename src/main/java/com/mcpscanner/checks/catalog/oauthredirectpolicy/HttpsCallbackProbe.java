package com.mcpscanner.checks.catalog.oauthredirectpolicy;

import java.util.List;
import java.util.Optional;

final class HttpsCallbackProbe implements RedirectProbe {

    @Override
    public List<RedirectFinding> run(ProbeContext context) {
        return context.clients().https()
                .map(client -> List.of(
                        DeliveryCase.of("http downgrade of the registered https URI",
                                CallbackUris.httpDowngradeOf(client.callback()), AuthorizationVariant.STANDARD),
                        DeliveryCase.normalising("dot-segment path beneath the registered URI",
                                CallbackUris.traversalBeneath(client.callback())))
                        .stream()
                        .map(deliveryCase -> context.delivery().probe(client, deliveryCase))
                        .flatMap(Optional::stream)
                        .toList())
                .orElse(List.of());
    }
}
