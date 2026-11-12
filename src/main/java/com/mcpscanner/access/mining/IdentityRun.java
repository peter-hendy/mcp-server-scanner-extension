package com.mcpscanner.access.mining;

record IdentityRun(String identityId, IdentitySightings sightings, int requestsSent, boolean limitReached,
                   MiningStop stop) {
}
