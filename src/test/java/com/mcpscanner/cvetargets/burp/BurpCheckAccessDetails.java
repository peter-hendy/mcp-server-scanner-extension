package com.mcpscanner.cvetargets.burp;

import com.mcpscanner.automation.CheckAccessPhase;
import com.mcpscanner.automation.CheckAccessStatus;
import com.mcpscanner.cvetargets.run.ArmProgressDetail;

import java.util.Locale;

public record BurpCheckAccessDetails(CheckAccessPhase status, int checked, int total, String reason)
        implements ArmProgressDetail {

    @Override
    public String progress() {
        return "Check access " + status.name().toLowerCase(Locale.ROOT) + ": " + checked + " of " + total
                + " items checked";
    }

    BurpCheckAccessDetails(CheckAccessStatus status) {
        this(status.status(), status.checked(), status.total(), status.reason());
    }
}
