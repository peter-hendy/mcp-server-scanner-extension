package com.mcpscanner.scan.request;

import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

final class FixedMessageRequests implements RequestFamilyBuilder {

    private final RequestFamily family;
    private final Predicate<ServerCapabilities> support;
    private final Supplier<McpMessage> message;

    FixedMessageRequests(RequestFamily family, Predicate<ServerCapabilities> support, Supplier<McpMessage> message) {
        this.family = family;
        this.support = support;
        this.message = message;
    }

    static FixedMessageRequests universal(RequestFamily family, Supplier<McpMessage> message) {
        return new FixedMessageRequests(family, capabilities -> true, message);
    }

    @Override
    public RequestFamily family() {
        return family;
    }

    @Override
    public boolean supportedBy(ServerSurface surface) {
        return support.test(surface.capabilities());
    }

    @Override
    public List<McpMessage> build(ServerSurface surface) {
        return List.of(message.get());
    }
}
