package com.mcpscanner.access.mining;

import com.mcpscanner.checks.api.Principal;
import com.mcpscanner.checks.api.PrincipalRoster;
import com.mcpscanner.scan.ScanInventory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

record MiningPlan(ScanInventory inventory, List<Principal> principals, Map<String, String> identityIdsByPrincipal) {

    static MiningPlan of(ScanInventory inventory, PrincipalRoster roster, String primaryUserId) {
        List<Principal> principals = roster.all();
        Map<String, String> identityIds = new LinkedHashMap<>();
        principals.forEach(principal -> identityIds.put(principal.id(),
                Principal.PRIMARY_ID.equals(principal.id()) ? primaryUserId : principal.id()));
        return new MiningPlan(inventory, principals, identityIds);
    }

    Set<String> identityIds() {
        return Set.copyOf(identityIdsByPrincipal.values());
    }

    String nameOf(String principalId) {
        return principals.stream().filter(principal -> principal.id().equals(principalId)).map(Principal::name)
                .findFirst().orElse(Principal.isAnonymous(principalId) ? Principal.anonymous().name()
                        : principalId);
    }
}
