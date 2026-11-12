package com.mcpscanner.identity;

import com.mcpscanner.auth.AuthStrategy;
import com.mcpscanner.checks.api.Principal;
import com.mcpscanner.checks.api.PrincipalRole;
import com.mcpscanner.checks.api.PrincipalRoster;

import java.util.function.Supplier;
import java.util.stream.Stream;

public final class IdentityRosterSource implements Supplier<PrincipalRoster> {

    private final Supplier<AuthStrategy> connectionAuth;
    private final IdentityRegistry registry;

    public IdentityRosterSource(Supplier<AuthStrategy> connectionAuth, IdentityRegistry registry) {
        this.connectionAuth = connectionAuth;
        this.registry = registry;
    }

    @Override
    public PrincipalRoster get() {
        return PrincipalRoster.of(primary(),
                registry.compared().stream().flatMap(this::principal).toList());
    }

    private Principal primary() {
        Identity connectAs = registry.primaryUser();
        return new Principal(Principal.PRIMARY_ID, connectAs.name(), PrincipalRole.PRIMARY,
                () -> connectionAuth.get().headers(), connectAs.privateMarker(),
                connectAs.ownedValues().ownerArguments(), () -> connectionAuth.get().currentHeaders());
    }

    static PrincipalRole principalRole(IdentityRole role) {
        return switch (role) {
            case PEER -> PrincipalRole.PEER;
            case LOWER -> PrincipalRole.LOWER;
            case HIGHER -> PrincipalRole.HIGHER;
        };
    }

    private Stream<Principal> principal(Identity identity) {
        return registry.credential(identity.id()).stream().map(credential -> new Principal(identity.id(),
                identity.name(), principalRole(identity.role()), credential::headers, identity.privateMarker(),
                identity.ownedValues().ownerArguments(), credential::currentHeaders));
    }
}
