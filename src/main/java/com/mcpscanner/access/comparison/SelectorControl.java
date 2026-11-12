package com.mcpscanner.access.comparison;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.mcp.Leaf;
import com.mcpscanner.checks.api.crossidentity.OperationSeed;
import com.mcpscanner.checks.api.crossidentity.ProbeKit;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

final class SelectorControl {

    private static final int MAX_CANDIDATES = 6;

    private final ProbeKit kit;

    SelectorControl(ProbeKit kit) {
        this.kit = kit;
    }

    Optional<Leaf> find(HttpRequest ownerRequest, Collection<String> ownedValues) {
        OperationSeed seed = kit.seed();
        return seed.selectorCandidates().stream()
                .filter(leaf -> ownedValues.contains(leaf.text()))
                .limit(MAX_CANDIDATES)
                .filter(leaf -> !kit.reply(control(ownerRequest, leaf)).success())
                .findFirst();
    }

    HttpRequest control(HttpRequest request, Leaf selector) {
        return kit.seed().withLeaves(request, Map.of(selector, kit.absent().sameShapeAs(selector)));
    }
}
