package com.mcpscanner.access.comparison;

import com.mcpscanner.checks.api.crossidentity.CredentialCopy;
import com.mcpscanner.logging.McpEventLog;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class SameCredentialNotice {

    private final McpEventLog eventLog;
    private final Set<String> noticed = ConcurrentHashMap.newKeySet();

    SameCredentialNotice(McpEventLog eventLog) {
        this.eventLog = eventLog;
    }

    void notice(CredentialCopy copy) {
        if (noticed.add(copy.copy().id() + "\n" + copy.original().id())) {
            eventLog.info("Check access: " + copy.copy().name() + " has the same credentials as "
                    + copy.original().name() + " — not compared");
        }
    }
}
