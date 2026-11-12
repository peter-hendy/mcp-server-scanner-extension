package com.mcpscanner.checks.catalog.jwtvalidation;

import com.mcpscanner.checks.api.CheckFactory;
import com.mcpscanner.checks.api.CheckLog;
import com.mcpscanner.checks.api.CheckServices;
import com.mcpscanner.checks.api.McpCheck;
import com.mcpscanner.checks.shared.auth.oracle.BaselineEquivalenceOracle;

import java.time.Clock;

public final class JwtValidationCheckFactory implements CheckFactory {

    @Override
    public McpCheck create(CheckServices services) {
        CheckLog log = new CheckLog(services.eventLog(), JwtValidationDescriptor.DESCRIPTOR.id());
        ForgedTokenAudit audit = new ForgedTokenAudit(ForgeryCatalog.defaultCatalog(),
                new PublishedKeyDiscovery(new JwkPemEncoder()), new BearerPlacement(),
                BaselineEquivalenceOracle.defaultOracle(), Clock.systemUTC(), log);
        return new JwtValidationCheck(new BearerJwtLocator(services.authStrategy()), audit, log);
    }
}
