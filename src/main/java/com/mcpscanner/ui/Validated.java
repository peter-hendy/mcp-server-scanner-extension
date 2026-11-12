package com.mcpscanner.ui;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

public record Validated<T>(Optional<T> value, Optional<String> problem) {

    public Validated {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(problem, "problem");
    }

    public static <T> Validated<T> of(Supplier<T> build) {
        try {
            return new Validated<>(Optional.of(build.get()), Optional.empty());
        } catch (IllegalArgumentException invalid) {
            return new Validated<>(Optional.empty(), Optional.of(invalid.getMessage()));
        }
    }
}
