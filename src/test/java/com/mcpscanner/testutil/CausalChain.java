package com.mcpscanner.testutil;

import java.util.stream.Stream;

public final class CausalChain {

    private CausalChain() {
    }

    public static Stream<Throwable> of(Throwable failure) {
        return Stream.iterate(failure, cause -> cause != null, Throwable::getCause);
    }
}
