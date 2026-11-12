package com.mcpscanner.checks.catalog.oauthconsentstructurereflection;

import com.mcpscanner.checks.shared.random.RandomHex;

final class StructureMarkers {

    private static final int NONCE_BYTES = 8;

    StructureMarker next() {
        return StructureMarker.withNonce(RandomHex.of(NONCE_BYTES));
    }
}
