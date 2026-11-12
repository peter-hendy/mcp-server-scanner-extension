package com.mcpscanner.scan.request;

import com.mcpscanner.mcp.McpToolDefinition;
import com.mcpscanner.scan.ScanInventory;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class RequestGenerator {

    private static final Comparator<RequestFamilyBuilder> INVOCATIONS_LAST =
            Comparator.comparing(builder -> builder.family().group().invokesInventoryItems());

    private final Map<RequestFamily, RequestFamilyBuilder> builders = new EnumMap<>(RequestFamily.class);

    public RequestGenerator(List<RequestFamilyBuilder> familyBuilders) {
        for (RequestFamilyBuilder builder : familyBuilders) {
            if (builders.putIfAbsent(builder.family(), builder) != null) {
                throw new IllegalArgumentException("Duplicate builder for request family " + builder.family());
            }
        }
    }

    public static RequestGenerator standard() {
        return new RequestGenerator(RequestFamilyCatalog.standard());
    }

    public GeneratedScan scanAll(ServerSurface surface, RequestFamilyToggles toggles) {
        List<ScanMessage> messages = builders.values().stream()
                .filter(builder -> toggles.isEnabled(builder.family()))
                .filter(builder -> builder.family().definedIn(surface.protocolVersion()))
                .filter(builder -> builder.supportedBy(surface))
                .sorted(INVOCATIONS_LAST)
                .<ScanMessage>flatMap(builder -> builder.build(surface).stream())
                .toList();
        return new GeneratedScan(messages, invokedTools(messages, surface.inventory()));
    }

    public GeneratedScan scanThisRequest(RequestFamily family, ScanInventory singleItem) {
        RequestFamilyBuilder builder = builders.get(family);
        if (builder == null) {
            throw new IllegalArgumentException("No builder for request family " + family);
        }
        List<ScanMessage> messages = List.copyOf(builder.build(new ServerSurface(singleItem, ServerCapabilities.none())));
        return new GeneratedScan(messages, invokedTools(messages, singleItem));
    }

    private static List<McpToolDefinition> invokedTools(List<ScanMessage> messages, ScanInventory inventory) {
        boolean callsTools = messages.stream().anyMatch(message -> message.family() == RequestFamily.TOOLS_CALL);
        return callsTools ? inventory.tools() : List.of();
    }
}
