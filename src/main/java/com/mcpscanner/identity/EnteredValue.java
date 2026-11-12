package com.mcpscanner.identity;

import java.util.Objects;

public record EnteredValue(String argument, String value) implements OwnedValue {

    public EnteredValue {
        Objects.requireNonNull(argument, "argument must not be null");
        Objects.requireNonNull(value, "value must not be null");
    }
}
