package com.mcpscanner.access;

import com.mcpscanner.access.comparison.ObjectReferences;
import com.mcpscanner.access.mining.MinedValueRule;
import com.mcpscanner.checks.api.AccessItemKey;
import com.mcpscanner.checks.api.Principal;

import java.util.Map;

public final class MatrixObjectReferences implements ObjectReferences {

    private final AccessMatrix matrix;
    private final MinedValueRule minedValues;

    public MatrixObjectReferences(AccessMatrix matrix, MinedValueRule minedValues) {
        this.matrix = matrix;
        this.minedValues = minedValues;
    }

    @Override
    public Map<String, String> of(Principal principal, AccessItemKey item) {
        return principal.ownedObjects().forTool(takesMinedValues(item));
    }

    private boolean takesMinedValues(AccessItemKey item) {
        return matrix.row(item)
                .map(row -> row.item().tool().map(minedValues::takesMinedValues).orElse(true))
                .orElse(false);
    }
}
