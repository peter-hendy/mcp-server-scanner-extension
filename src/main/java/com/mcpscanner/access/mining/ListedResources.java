package com.mcpscanner.access.mining;

import com.mcpscanner.scan.UriTemplateExpansion;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

final class ListedResources {

    private static final String URI = "uri";

    private final MiningCatalog catalog;

    ListedResources(MiningCatalog catalog) {
        this.catalog = catalog;
    }

    List<Sighting> sightings(MiningCall call, JsonNode result) {
        List<Sighting> sightings = new ArrayList<>();
        JsonNode resources = result.path("resources");
        for (int index = 0; index < resources.size(); index++) {
            JsonNode uri = resources.get(index).path(URI);
            if (uri.isTextual()) {
                String path = "resources[" + index + "]." + URI;
                sightings.add(new Sighting(uri, Set.of(URI), List.of(URI), call.operation(), path,
                        Sighting.Kind.LISTED_RESOURCE));
                sightings.addAll(variables(call, uri.asText(), path));
            }
        }
        return sightings;
    }

    private List<Sighting> variables(MiningCall call, String uri, String path) {
        List<Sighting> sightings = new ArrayList<>();
        for (TemplateArguments template : catalog.templates()) {
            UriTemplateExpansion.match(template.uriTemplate(), uri).ifPresent(values -> values.forEach((variable, value) ->
                    variable(call, template, variable, value, path).ifPresent(sightings::add)));
        }
        return sightings;
    }

    private Optional<Sighting> variable(MiningCall call, TemplateArguments template, String variable,
                                                  String value, String path) {
        JsonNode node = TextNode.valueOf(value);
        if (!IdShape.idShaped(node)) {
            return Optional.empty();
        }
        Set<String> aliases = template.slots().stream().filter(slot -> slot.name().equals(variable))
                .findFirst().map(ArgumentSlot::aliases).orElse(Set.of(Aliases.normalise(variable)));
        return Optional.of(new Sighting(node, aliases, catalog.argumentsTaking(aliases, node),
                call.operation(), path, Sighting.Kind.LISTED_VARIABLE));
    }
}
