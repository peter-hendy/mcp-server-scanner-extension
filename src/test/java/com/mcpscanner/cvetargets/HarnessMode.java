package com.mcpscanner.cvetargets;

import com.mcpscanner.cvetargets.burp.BurpHarness;
import com.mcpscanner.cvetargets.burp.BurpSettings;
import com.mcpscanner.cvetargets.run.CveTargetHarness;
import com.mcpscanner.cvetargets.run.HarnessSettings;

import java.util.Arrays;

enum HarnessMode {
    DIRECT {
        @Override
        CveTargetHarness harness(HarnessSettings settings) {
            if (settings.allChecks()) {
                throw new IllegalArgumentException("all-checks mode needs Burp: run ./gradlew cveTargetsBurp -PallChecks");
            }
            return CveTargetHarness.docker(settings);
        }
    },
    BURP {
        @Override
        CveTargetHarness harness(HarnessSettings settings) {
            return BurpHarness.docker(settings, BurpSettings.fromSystemProperties());
        }
    };

    static final String PROPERTY = "cveTargets.mode";

    abstract CveTargetHarness harness(HarnessSettings settings);

    static HarnessMode fromSystemProperties() {
        String mode = System.getProperty(PROPERTY, DIRECT.name());
        return Arrays.stream(values()).filter(value -> value.name().equalsIgnoreCase(mode.strip())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException(PROPERTY + " must be direct or burp, got " + mode));
    }
}
