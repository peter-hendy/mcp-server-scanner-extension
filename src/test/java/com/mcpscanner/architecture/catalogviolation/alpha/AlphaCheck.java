package com.mcpscanner.architecture.catalogviolation.alpha;

import com.mcpscanner.architecture.catalogviolation.beta.BetaHelper;
import com.mcpscanner.checks.registry.ScanCheckSettings;

public final class AlphaCheck {

    public String borrow(ScanCheckSettings settings) {
        return BetaHelper.help() + settings;
    }
}
