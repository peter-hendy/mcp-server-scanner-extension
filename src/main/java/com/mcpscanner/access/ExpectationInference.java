package com.mcpscanner.access;

import com.mcpscanner.checks.api.AccessExpectation;
import com.mcpscanner.checks.api.CredentialCopies;
import com.mcpscanner.checks.api.Principal;
import com.mcpscanner.checks.api.PrincipalRole;

import java.util.List;

final class ExpectationInference {

    private ExpectationInference() {
    }

    static AccessExpectation infer(ListedItem item, List<Principal> roster, CredentialCopies copies) {
        List<Principal> principals = roster.stream().filter(principal -> !copies.isCopy(principal)).toList();
        List<Principal> listers = withListing(item, principals, ListingState.LISTED);
        if (listers.stream().allMatch(principal -> principal.role() == PrincipalRole.HIGHER)) {
            return AccessExpectation.HIGHER_ONLY;
        }
        List<Principal> others = principals.stream().filter(principal -> !principal.id().equals(item.owner().id()))
                .filter(principal -> discovered(item.listing(principal))).toList();
        List<Principal> hidden = withListing(item, others, ListingState.HIDDEN);
        if (hidden.isEmpty()) {
            return AccessExpectation.ANY_SIGNED_IN;
        }
        if (hidden.size() == others.size()) {
            return AccessExpectation.OWNER_ONLY;
        }
        return hidden.stream().allMatch(principal -> principal.role() == PrincipalRole.LOWER)
                ? AccessExpectation.NOT_LOWER : AccessExpectation.ANY_SIGNED_IN;
    }

    private static List<Principal> withListing(ListedItem item, List<Principal> principals, ListingState state) {
        return principals.stream().filter(principal -> item.listing(principal) == state).toList();
    }

    private static boolean discovered(ListingState state) {
        return state == ListingState.LISTED || state == ListingState.HIDDEN;
    }
}
