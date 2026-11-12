package com.mcpscanner.checks.api.crossidentity;

import com.mcpscanner.checks.api.CredentialCopies;
import com.mcpscanner.checks.api.Principal;
import com.mcpscanner.checks.api.PrincipalRoster;

import java.util.List;
import java.util.Optional;

public record ComparedPrincipals(List<Principal> compared, List<CredentialCopy> copies) {

    public static ComparedPrincipals of(PrincipalRoster roster, Principal owner) {
        List<Principal> all = roster.all();
        CredentialCopies credentialCopies = new CredentialCopies(owner, all);
        List<Principal> credentialed = all.stream()
                .filter(principal -> !principal.id().equals(owner.id()))
                .filter(principal -> !principal.credentialHeaders().get().isEmpty())
                .toList();
        return new ComparedPrincipals(
                credentialed.stream().filter(principal -> !credentialCopies.isCopy(principal)).toList(),
                credentialed.stream().flatMap(principal -> copyOf(principal, credentialCopies).stream()).toList());
    }

    private static Optional<CredentialCopy> copyOf(Principal principal, CredentialCopies credentialCopies) {
        return credentialCopies.originalOf(principal).map(original -> new CredentialCopy(principal, original));
    }
}
