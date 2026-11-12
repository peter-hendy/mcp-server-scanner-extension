package com.mcpscanner.cvetargets.identities;

import com.mcpscanner.checks.api.AccessItemKey;
import com.mcpscanner.checks.api.AccessOutcome;

public record AccessRecord(AccessItemKey.Kind kind, String item, String principal, AccessOutcome outcome) {
}
