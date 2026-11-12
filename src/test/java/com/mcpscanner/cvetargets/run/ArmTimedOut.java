package com.mcpscanner.cvetargets.run;

import java.math.BigDecimal;
import java.time.Duration;

final class ArmTimedOut extends RuntimeException {

    ArmTimedOut(Duration limit) {
        super("arm timed out after " + describe(limit));
    }

    private static String describe(Duration limit) {
        if (limit.toSeconds() >= 60 && limit.toMillis() % 60_000 == 0) {
            return limit.toMinutes() + " min";
        }
        return BigDecimal.valueOf(limit.toMillis(), 3).stripTrailingZeros().toPlainString() + " s";
    }
}
