package com.mcpscanner.checks.shared.oauth;

import java.util.Set;

public final class RedirectStatus {

    private static final Set<Integer> REDIRECTS = Set.of(301, 302, 303, 307, 308);

    private RedirectStatus() {
    }

    public static boolean isRedirect(int status) {
        return REDIRECTS.contains(status);
    }
}
