package com.mcpscanner.client;

import com.mcpscanner.config.ResourceCap;

import java.util.Optional;
import java.util.function.ToLongFunction;

final class PaginationBudget {
    private final long maxPages;
    private final long maxBytes;
    private long pages;
    private long bytes;

    PaginationBudget(ToLongFunction<ResourceCap> caps) {
        this.maxPages = caps.applyAsLong(ResourceCap.DISCOVERY_MAX_PAGES);
        this.maxBytes = caps.applyAsLong(ResourceCap.DISCOVERY_TOTAL_BYTES);
    }

    Optional<ResourceCap> record(long pageBytes) {
        pages++;
        bytes += pageBytes;
        if (pages >= maxPages) return Optional.of(ResourceCap.DISCOVERY_MAX_PAGES);
        if (bytes >= maxBytes) return Optional.of(ResourceCap.DISCOVERY_TOTAL_BYTES);
        return Optional.empty();
    }

    long pages() {
        return pages;
    }

    long bytes() {
        return bytes;
    }
}
