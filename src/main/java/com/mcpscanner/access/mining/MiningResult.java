package com.mcpscanner.access.mining;

import java.util.List;
import java.util.Optional;

public record MiningResult(List<IdentityMining> identities, List<MiningWarning> warnings, MiningStop stop) {

    public MiningResult {
        identities = List.copyOf(identities);
        warnings = List.copyOf(warnings);
    }

    public Optional<IdentityMining> of(String identityId) {
        return identities.stream().filter(mining -> mining.identityId().equals(identityId)).findFirst();
    }
}
