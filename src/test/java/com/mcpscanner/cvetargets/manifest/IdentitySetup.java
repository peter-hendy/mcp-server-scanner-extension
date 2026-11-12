package com.mcpscanner.cvetargets.manifest;

import java.util.List;
import java.util.stream.Stream;

public record IdentitySetup(String provision, PrimaryIdentity primary, List<OtherIdentity> others,
                            List<AccessItem> items) {

    public static final String ANONYMOUS_CALLER = "anonymous";
    public static final String PRIMARY_CALLER = "primary";
    public static final String ON_PRIMARY_SESSION_SUFFIX = "@primary-session";

    public IdentitySetup {
        primary = primary == null ? PrimaryIdentity.unnamed() : primary;
        others = others == null ? List.of() : List.copyOf(others);
        items = items == null ? List.of() : List.copyOf(items);
    }

    public static IdentitySetup none() {
        return new IdentitySetup(null, null, List.of(), List.of());
    }

    public boolean declared() {
        return !others.isEmpty() || provision != null;
    }

    public List<SeedRequest> itemSeeds() {
        return items.stream().map(AccessItem::seed).toList();
    }

    public Stream<String> callers() {
        return Stream.concat(Stream.of(ANONYMOUS_CALLER, PRIMARY_CALLER), others.stream()
                .flatMap(other -> Stream.of(other.id(), other.id() + ON_PRIMARY_SESSION_SUFFIX)));
    }
}
