package com.mcpscanner.identity;

import java.util.List;
import java.util.function.UnaryOperator;

public final class RegistryOwnedValues implements OwnedValueStore {

    private final IdentityRegistry registry;

    public RegistryOwnedValues(IdentityRegistry registry) {
        this.registry = registry;
    }

    @Override
    public OwnedValues valuesOf(String identityId) {
        return registry.require(identityId).ownedValues();
    }

    @Override
    public void replaceMined(String identityId, List<MinedValue> values) {
        registry.modify(identityId, changing(owned -> owned.withMined(values)));
    }

    @Override
    public void replaceEntered(String identityId, List<EnteredValue> values) {
        registry.modify(identityId, changing(owned -> owned.withEntered(values)));
    }

    @Override
    public void clearMined(String identityId) {
        registry.modify(identityId, changing(OwnedValues::withoutMined));
    }

    @Override
    public void clearAllMined() {
        registry.modifyAll(changing(OwnedValues::withoutMined));
    }

    @Override
    public void addListener(Runnable listener) {
        registry.addListener(listener);
    }

    @Override
    public void removeListener(Runnable listener) {
        registry.removeListener(listener);
    }

    private static UnaryOperator<Identity> changing(UnaryOperator<OwnedValues> change) {
        return identity -> identity.withOwnedValues(change.apply(identity.ownedValues()));
    }
}
