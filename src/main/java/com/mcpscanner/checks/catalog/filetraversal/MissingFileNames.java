package com.mcpscanner.checks.catalog.filetraversal;

import com.mcpscanner.checks.shared.random.RandomHex;

import java.util.function.Supplier;

final class MissingFileNames implements Supplier<String> {

    private static final String PREFIX = "mcpscan-";
    private static final int NAME_BYTES = 5;

    @Override
    public String get() {
        return PREFIX + RandomHex.of(NAME_BYTES);
    }
}
