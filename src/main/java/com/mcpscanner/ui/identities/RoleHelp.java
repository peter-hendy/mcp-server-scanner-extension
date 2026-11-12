package com.mcpscanner.ui.identities;

import com.mcpscanner.identity.Identity;

final class RoleHelp {

    private RoleHelp() {
    }

    static String describe(Identity user, Identity primary) {
        String name = user.name();
        String baseline = primary.name();
        return switch (user.role()) {
            case PEER -> "A finding when " + name + " reaches " + baseline + "'s private objects.";
            case LOWER -> "A finding when " + name + " runs an operation only " + baseline + " should.";
            case HIGHER -> "A finding when " + baseline + " runs an operation only " + name + " should.";
        };
    }
}
