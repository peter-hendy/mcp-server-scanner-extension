package com.mcpscanner.ui.identities;

import com.mcpscanner.identity.Identity;
import com.mcpscanner.identity.IdentityAuthKind;
import com.mcpscanner.identity.IdentityNames;
import com.mcpscanner.identity.IdentityRole;

import java.util.function.Consumer;
import java.util.function.Supplier;

final class IdentityCommands {

    private static final String USER = "User";

    private final IdentityUiServices services;
    private final Consumer<String> errors;
    private final Consumer<String> select;

    IdentityCommands(IdentityUiServices services, Consumer<String> errors, Consumer<String> select) {
        this.services = services;
        this.errors = errors;
        this.select = select;
    }

    void addStarter(IdentityRole role) {
        Identity primary = services.registry().primaryUser();
        add(() -> Identity.create(unusedName(starterName(role)), role, starterKind(primary.authKind()))
                .withHeaderNames(primary.headerNames()));
    }

    void addNone() {
        add(() -> Identity.create(unusedName(USER), IdentityRole.PEER, IdentityAuthKind.NONE));
    }

    void addBearer() {
        add(() -> Identity.create(unusedName(USER), IdentityRole.PEER, IdentityAuthKind.BEARER));
    }

    void addCustomHeaders() {
        add(() -> Identity.create(unusedName(USER), IdentityRole.PEER, IdentityAuthKind.CUSTOM_HEADERS)
                .withHeaderNames(services.registry().primaryUser().headerNames()));
    }

    void addOAuth() {
        add(() -> Identity.create(unusedName(USER), IdentityRole.PEER, IdentityAuthKind.OAUTH));
    }

    void duplicate(String identityId) {
        attempt(() -> select.accept(services.registry().duplicate(identityId).id()));
    }

    void remove(String identityId) {
        attempt(() -> {
            services.signIns().cancel(identityId);
            services.registry().remove(identityId);
        });
    }

    void signIn(String identityId) {
        attempt(() -> services.signIns().signIn(identityId));
    }

    void cancelSignIn(String identityId) {
        attempt(() -> services.signIns().cancel(identityId));
    }

    void signInAll() {
        attempt(() -> services.signIns().signInAll());
    }

    void setCompared(Identity identity, boolean compared) {
        update(identity.withCompared(compared));
    }

    boolean update(Identity edited) {
        return attempt(() -> services.registry().update(edited));
    }

    void supplyCredential(Runnable supply) {
        attempt(supply);
    }

    private void add(Supplier<Identity> identity) {
        attempt(() -> select.accept(services.registry().add(identity.get()).id()));
    }

    private boolean attempt(Runnable action) {
        try {
            action.run();
            errors.accept(null);
            return true;
        } catch (IllegalArgumentException | IllegalStateException refused) {
            errors.accept(refused.getMessage());
            return false;
        }
    }

    private String unusedName(String base) {
        return IdentityNames.of(services.registry().identities()).unused(base);
    }

    private static IdentityAuthKind starterKind(IdentityAuthKind primaryKind) {
        return primaryKind == IdentityAuthKind.NONE || !primaryKind.offeredToComparedUsers()
                ? IdentityAuthKind.BEARER : primaryKind;
    }

    private static String starterName(IdentityRole role) {
        return switch (role) {
            case PEER -> "Peer";
            case LOWER -> "Lower-privilege user";
            case HIGHER -> "Higher-privilege user";
        };
    }
}
