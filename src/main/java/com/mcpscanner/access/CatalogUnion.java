package com.mcpscanner.access;

import com.mcpscanner.checks.api.AccessItemKey;
import com.mcpscanner.checks.api.Principal;
import com.mcpscanner.identity.discovery.DiscoveryOutcome;
import com.mcpscanner.identity.discovery.IdentityListing;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class CatalogUnion {

    private final List<Principal> principals;
    private final Map<String, IdentityListing> listings = new LinkedHashMap<>();

    CatalogUnion(List<Principal> principals, Map<String, DiscoveryOutcome> outcomes) {
        this.principals = List.copyOf(principals);
        principals.forEach(principal -> listed(outcomes.get(principal.id()))
                .ifPresent(listing -> listings.put(principal.id(), listing)));
    }

    List<ListedItem> items() {
        return keys().stream().map(this::listedItem).toList();
    }

    Optional<ListedItem> find(AccessItemKey key) {
        return principals.stream().anyMatch(principal -> lists(principal, key))
                ? Optional.of(listedItem(key)) : Optional.empty();
    }

    private Set<AccessItemKey> keys() {
        Set<AccessItemKey> keys = new LinkedHashSet<>();
        listings.values().forEach(listing -> listing.toolNames()
                .forEach(name -> keys.add(new AccessItemKey(AccessItemKey.Kind.TOOL, name))));
        listings.values().forEach(listing -> listing.resourceUris()
                .forEach(uri -> keys.add(new AccessItemKey(AccessItemKey.Kind.RESOURCE, uri))));
        return keys;
    }

    private ListedItem listedItem(AccessItemKey key) {
        Principal owner = owner(key);
        return new ListedItem(definition(listings.get(owner.id()), key), owner, listingStates(key));
    }

    private Principal owner(AccessItemKey key) {
        return principals.stream()
                .filter(principal -> lists(principal, key))
                .min(Comparator.comparing((Principal principal) -> !Principal.PRIMARY_ID.equals(principal.id()))
                        .thenComparing(principal -> -principal.role().rank()))
                .orElseThrow();
    }

    private Map<String, ListingState> listingStates(AccessItemKey key) {
        Map<String, ListingState> states = new LinkedHashMap<>();
        principals.forEach(principal -> states.put(principal.id(), listingState(principal, key)));
        return states;
    }

    private ListingState listingState(Principal principal, AccessItemKey key) {
        IdentityListing listing = listings.get(principal.id());
        if (listing == null) {
            return ListingState.NOT_DISCOVERED;
        }
        return listing.lists(key) ? ListingState.LISTED : ListingState.HIDDEN;
    }

    private boolean lists(Principal principal, AccessItemKey key) {
        return listingState(principal, key) == ListingState.LISTED;
    }

    private static AccessItem definition(IdentityListing listing, AccessItemKey key) {
        return switch (key.kind()) {
            case TOOL -> AccessItem.tool(listing.tools().get(key.name()));
            case RESOURCE -> AccessItem.resource(listing.resources().get(key.name()));
        };
    }

    private static Optional<IdentityListing> listed(DiscoveryOutcome outcome) {
        return outcome instanceof DiscoveryOutcome.Listed listed ? Optional.of(listed.listing()) : Optional.empty();
    }
}
