package com.mcpscanner.access.mining;

import com.mcpscanner.scan.UriTemplateExpansion;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

final class CallPlanner {

    private final MiningCatalog catalog;
    private final ToolGate gate;

    CallPlanner(MiningCatalog catalog, ToolGate gate) {
        this.catalog = catalog;
        this.gate = gate;
    }

    List<MiningCall> firstPass() {
        return Stream.of(
                        callableTools().map(tool -> new MiningCall.ToolCall(tool.tool(), Map.of())),
                        catalog.inventory().resources().stream().map(resource -> new MiningCall.ResourceRead(resource.uri())),
                        Stream.of(new MiningCall.ResourceListing()),
                        catalog.inventory().prompts().stream().map(MiningCall.PromptGet::new))
                .<MiningCall>flatMap(calls -> calls)
                .toList();
    }

    List<MiningCall> fedPass(IdentitySightings sightings) {
        return Stream.of(
                        callableTools().flatMap(tool -> fedToolCalls(tool, sightings)),
                        catalog.templates().stream().flatMap(template -> fedTemplateReads(template, sightings)),
                        sightings.listedResources().stream().map(MiningCall.ResourceRead::new))
                .<MiningCall>flatMap(calls -> calls)
                .toList();
    }

    private Stream<ToolArguments> callableTools() {
        return catalog.tools().stream().filter(tool -> gate.mayCall(tool.tool()));
    }

    private static Stream<MiningCall> fedToolCalls(ToolArguments tool, IdentitySightings sightings) {
        return tool.slots().stream().flatMap(slot -> sightings.feedableFor(slot).stream()
                .map(sighting -> new MiningCall.ToolCall(tool.tool(),
                        Map.of(slot.name(), slot.type().render(sighting.value())))));
    }

    private static Stream<MiningCall> fedTemplateReads(TemplateArguments template, IdentitySightings sightings) {
        return template.slots().stream().flatMap(slot -> sightings.feedableFor(slot).stream()
                .map(sighting -> new MiningCall.ResourceRead(UriTemplateExpansion.expandWith(
                        template.uriTemplate(), Map.of(slot.name(), sighting.value())))));
    }
}
