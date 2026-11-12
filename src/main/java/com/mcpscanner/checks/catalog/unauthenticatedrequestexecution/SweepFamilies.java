package com.mcpscanner.checks.catalog.unauthenticatedrequestexecution;

import com.mcpscanner.checks.shared.auth.VariantFamily;

import java.util.EnumSet;
import java.util.Set;

final class SweepFamilies {

    private SweepFamilies() {}

    static Set<VariantFamily> forDirectDelivery() {
        return Set.copyOf(EnumSet.allOf(VariantFamily.class));
    }

    static Set<VariantFamily> forStreamProxy() {
        return Set.copyOf(EnumSet.complementOf(EnumSet.of(VariantFamily.PATH, VariantFamily.SESSION)));
    }
}
