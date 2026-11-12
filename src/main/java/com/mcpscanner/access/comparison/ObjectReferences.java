package com.mcpscanner.access.comparison;

import com.mcpscanner.checks.api.AccessItemKey;
import com.mcpscanner.checks.api.Principal;

import java.util.Map;

@FunctionalInterface
public interface ObjectReferences {

    ObjectReferences ALL = (principal, item) -> principal.ownedObjects().all();

    Map<String, String> of(Principal principal, AccessItemKey item);
}
