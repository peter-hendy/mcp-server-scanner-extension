package com.mcpscanner.checks.catalog.filetraversal;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

final class TraversalPayloads {

    private static final Comparator<TraversalPayload> STRONGEST_FIRST = Comparator
            .comparing((TraversalPayload payload) -> payload.file().repositoryMetadata())
            .thenComparing(TraversalPayload::tier);

    private TraversalPayloads() {}

    static List<TraversalPayload> forBaseValue(String baseValue) {
        BasePath base = BasePath.of(baseValue);
        return Stream.of(HostFilePayloads.forBase(HostFileSpelling.PASSWD, base),
                        HostFilePayloads.forBase(HostFileSpelling.WIN_INI, base),
                        GitConfigPayloads.forBase(base))
                .flatMap(List::stream)
                .sorted(STRONGEST_FIRST)
                .toList();
    }
}
