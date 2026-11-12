package com.mcpscanner.access;

import com.mcpscanner.checks.api.Principal;

import java.util.Map;

record ListedItem(AccessItem item, Principal owner, Map<String, ListingState> listings) {

    ListingState listing(Principal principal) {
        return listings.getOrDefault(principal.id(), ListingState.NOT_DISCOVERED);
    }
}
