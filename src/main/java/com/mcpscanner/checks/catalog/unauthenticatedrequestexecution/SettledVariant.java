package com.mcpscanner.checks.catalog.unauthenticatedrequestexecution;

import com.mcpscanner.checks.shared.auth.AuthVariant;
import com.mcpscanner.checks.shared.auth.VariantFamily;

record SettledVariant(String name, String sessionValue) {

    static SettledVariant of(AuthVariant variant) {
        String sessionValue = variant.family() == VariantFamily.SESSION ? variant.candidate().value() : "";
        return new SettledVariant(variant.name(), sessionValue);
    }
}
