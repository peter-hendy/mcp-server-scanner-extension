package com.mcpscanner.ui.access;

import com.mcpscanner.identity.Identity;

record UserChoice(String id, String name) {

    static UserChoice of(Identity identity) {
        return new UserChoice(identity.id(), identity.name());
    }

    @Override
    public String toString() {
        return name;
    }
}
