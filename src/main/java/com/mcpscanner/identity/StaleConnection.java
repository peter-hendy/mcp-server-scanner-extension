package com.mcpscanner.identity;

import java.util.Objects;

public record StaleConnection(Identity user) {

    public StaleConnection {
        Objects.requireNonNull(user, "user");
    }
}
