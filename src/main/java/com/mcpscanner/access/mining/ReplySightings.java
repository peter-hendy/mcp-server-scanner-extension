package com.mcpscanner.access.mining;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class ReplySightings {

    private static final Set<String> PAGINATION_KEYS = Set.of("cursor", "nextcursor", "prevcursor", "previouscursor",
            "pagetoken", "nextpagetoken", "continuationtoken", "nexttoken", "startcursor", "endcursor", "offset",
            "limit", "page", "pagesize", "perpage", "pagenumber", "total", "totalcount");
    private static final String WRAPPED_RESULT = "result";
    private static final String ID = "id";

    private final MiningCatalog catalog;
    private final ListedResources listings;

    ReplySightings(MiningCatalog catalog) {
        this.catalog = catalog;
        this.listings = new ListedResources(catalog);
    }

    List<Sighting> stable(MiningCall call, MiningReply first, MiningReply second) {
        List<Sighting> repeated = of(call, second);
        return of(call, first).stream()
                .filter(sighting -> repeated.stream().anyMatch(sighting::sameAs))
                .toList();
    }

    private List<Sighting> of(MiningCall call, MiningReply reply) {
        if (!reply.usable()) {
            return List.of();
        }
        return call instanceof MiningCall.ResourceListing ? listings.sightings(call, reply.result())
                : fields(call, reply);
    }

    private List<Sighting> fields(MiningCall call, MiningReply reply) {
        return ReplyLeaves.of(reply.result()).stream()
                .filter(field -> !PAGINATION_KEYS.contains(Aliases.normalise(field.key())))
                .filter(field -> !SecretName.matches(field.key()) && !SecretName.matches(field.parentKey()))
                .filter(field -> IdShape.idShaped(field.leaf().value()))
                .map(field -> sighting(call, field))
                .filter(sighting -> !sighting.arguments().isEmpty())
                .toList();
    }

    private static Set<String> aliases(MiningCall call, ReplyField field) {
        Set<String> aliases = new HashSet<>(Aliases.ofLeaf(field.key(), field.parentKey()));
        if (call instanceof MiningCall.ToolCall tool && field.parentKey() == null
                && (wrapped(field) || ID.equals(Aliases.normalise(field.key())))) {
            aliases.addAll(Aliases.ofSubject(tool.tool().name()));
        }
        return aliases;
    }

    private static boolean wrapped(ReplyField field) {
        return WRAPPED_RESULT.equals(field.key()) && field.parentKey() == null;
    }

    private Sighting sighting(MiningCall call, ReplyField field) {
        Set<String> aliases = aliases(call, field);
        JsonNode value = field.leaf().value();
        return new Sighting(value, aliases, catalog.argumentsTaking(aliases, value), call.operation(),
                field.path(), Sighting.Kind.REPLY_FIELD);
    }
}
