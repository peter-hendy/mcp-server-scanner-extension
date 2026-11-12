package com.mcpscanner.access.mining;

import com.mcpscanner.checks.api.Principal;

@FunctionalInterface
public interface MiningSender {

    MiningReply send(Principal identity, MiningCall call);
}
