package com.mcpscanner.checks.api;

import java.util.List;
import java.util.Optional;

public final class CredentialCopies {

    private final Principal owner;
    private final List<Principal> principals;

    public CredentialCopies(Principal owner, List<Principal> principals) {
        this.owner = owner;
        this.principals = List.copyOf(principals);
    }

    public Optional<Principal> originalOf(Principal principal) {
        if (isOwner(principal)) {
            return Optional.empty();
        }
        if (principal.sharesCredentialsWith(owner)) {
            return Optional.of(owner);
        }
        return earlierThan(principal).stream()
                .filter(candidate -> !isOwner(candidate))
                .filter(principal::sharesCredentialsWith)
                .findFirst();
    }

    public boolean isCopy(Principal principal) {
        return originalOf(principal).isPresent();
    }

    private List<Principal> earlierThan(Principal principal) {
        int position = indexOf(principal);
        return position < 0 ? List.of() : principals.subList(0, position);
    }

    private int indexOf(Principal principal) {
        for (int index = 0; index < principals.size(); index++) {
            if (principals.get(index).id().equals(principal.id())) {
                return index;
            }
        }
        return -1;
    }

    private boolean isOwner(Principal principal) {
        return principal.id().equals(owner.id());
    }
}
