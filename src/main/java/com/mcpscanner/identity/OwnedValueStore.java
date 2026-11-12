package com.mcpscanner.identity;

import java.util.List;

public interface OwnedValueStore {

    OwnedValues valuesOf(String identityId);

    void replaceMined(String identityId, List<MinedValue> values);

    void replaceEntered(String identityId, List<EnteredValue> values);

    void clearMined(String identityId);

    void clearAllMined();

    void addListener(Runnable listener);

    void removeListener(Runnable listener);
}
