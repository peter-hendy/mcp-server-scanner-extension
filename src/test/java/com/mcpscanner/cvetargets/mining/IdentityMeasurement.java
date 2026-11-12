package com.mcpscanner.cvetargets.mining;

import com.mcpscanner.access.mining.IdentityMining;
import com.mcpscanner.access.mining.MiningWarning;
import com.mcpscanner.identity.MinedValue;
import com.mcpscanner.identity.ValueScope;

import java.util.List;
import java.util.Map;

public record IdentityMeasurement(String identityId, String name, List<OwnedObjectCheck> ownedObjects,
                                  int privateCount, int sharedCount, int requestsSent, List<String> warnings,
                                  List<String> privateValues, List<String> allValues) {

    private static final int SAMPLE = 8;

    public static IdentityMeasurement of(String name, IdentityMining mining, Map<String, String> declaredOwned) {
        return new IdentityMeasurement(mining.identityId(), name,
                declaredOwned.entrySet().stream().map(owned -> check(owned.getKey(), owned.getValue(), mining)).toList(),
                mining.privateValues().size(), mining.values().size() - mining.privateValues().size(),
                mining.requestsSent(), mining.warnings().stream().map(MiningWarning::kind).map(Enum::name).toList(),
                mining.privateValues().stream().map(value -> value.argument() + "=" + value.value()).distinct()
                        .limit(SAMPLE).toList(),
                mining.values().stream().map(value -> value.argument() + "=" + value.value() + " ["
                        + value.scope() + " from " + value.source().operation() + " " + value.source().fieldPath() + "]")
                        .toList());
    }

    private static OwnedObjectCheck check(String argument, String value, IdentityMining mining) {
        List<MinedValue> matches = mining.values().stream().filter(mined -> mined.value().equals(value)).toList();
        return new OwnedObjectCheck(argument, value, !matches.isEmpty(),
                matches.stream().anyMatch(mined -> mined.scope() == ValueScope.PRIVATE));
    }
}
