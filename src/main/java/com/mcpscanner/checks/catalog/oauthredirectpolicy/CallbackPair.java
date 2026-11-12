package com.mcpscanner.checks.catalog.oauthredirectpolicy;

import java.net.URI;
import java.util.List;

record CallbackPair(URI registered, URI candidate) {

    boolean confirmedBy(List<String> registeredCallbacks) {
        return registeredCallbacks.contains(registered.toString())
                && !registeredCallbacks.contains(candidate.toString())
                && !registered.equals(candidate)
                && candidate.getRawQuery() == null
                && candidate.getRawFragment() == null;
    }

    CallbackPair withCandidate(URI replacement) {
        return new CallbackPair(registered, replacement);
    }
}
