package com.mcpscanner.architecture;

import com.mcpscanner.architecture.catalogviolation.alpha.AlphaCheck;

final class CatalogViolationRegistrar {

    private CatalogViolationRegistrar() {
    }

    static AlphaCheck register() {
        return new AlphaCheck();
    }
}
