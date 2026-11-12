package com.mcpscanner.cvetargets.identities;

import com.mcpscanner.checks.api.AccessItemKey;

import java.util.List;
import java.util.Optional;

public record ItemListing(List<String> tools, List<String> resources) {

    Optional<Boolean> lists(AccessItemKey item) {
        List<String> names = item.kind() == AccessItemKey.Kind.TOOL ? tools : resources;
        return Optional.ofNullable(names).map(listed -> listed.contains(item.name()));
    }
}
