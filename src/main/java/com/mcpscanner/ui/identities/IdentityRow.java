package com.mcpscanner.ui.identities;

import com.mcpscanner.identity.Identity;

import java.util.Optional;

sealed interface IdentityRow {

    default Optional<Identity> identity() {
        return Optional.empty();
    }

    enum Anonymous implements IdentityRow { INSTANCE }

    record Member(Identity member) implements IdentityRow {
        @Override
        public Optional<Identity> identity() {
            return Optional.of(member);
        }
    }
}
