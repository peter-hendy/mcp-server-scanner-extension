package com.mcpscanner.core.oauth;

import java.util.LinkedHashSet;
import java.util.Set;

final class ScopeValues {
    static Set<String> parse(String value) {
        if (value.isEmpty()) throw new OAuthPolicyException(OAuthPolicyException.Reason.INVALID_CHALLENGE);
        var scopes = new LinkedHashSet<String>();
        for (String scope : value.split(" ", -1)) {
            if (!valid(scope)) throw new OAuthPolicyException(OAuthPolicyException.Reason.INVALID_CHALLENGE);
            scopes.add(scope);
        }
        return Set.copyOf(scopes);
    }
    static boolean valid(String value) {
        return value != null && !value.isEmpty() && value.chars().allMatch(c -> c == 0x21 || c >= 0x23 && c <= 0x5b || c >= 0x5d && c <= 0x7e);
    }
}
