package com.mcpscanner.scan;

import com.mcpscanner.config.ExtensionConfigStore;
import com.mcpscanner.testutil.InMemoryPersistedObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScanModeSettingTest {

    private final ExtensionConfigStore store = InMemoryPersistedObject.configStore();
    private final ScanModeSetting setting = new ScanModeSetting(store);

    @Test
    void scansWithMcpChecksOnlyUntilTheUserChoosesOtherwise() {
        assertThat(setting.current()).isEqualTo(ScanMode.MCP_ONLY);
    }

    @ParameterizedTest
    @EnumSource(ScanMode.class)
    void theChosenModeSurvivesAReload(ScanMode mode) {
        setting.select(mode);

        assertThat(new ScanModeSetting(store).current()).isEqualTo(mode);
    }

    @Test
    void anUnrecognisedSavedModeFallsBackToMcpChecksOnly() {
        store.setScanMode("FUTURE_MODE");

        assertThat(setting.current()).isEqualTo(ScanMode.MCP_ONLY);
    }

    @Test
    void selectingAModeTellsEveryListener() {
        List<ScanMode> heard = new ArrayList<>();
        setting.addListener(heard::add);

        setting.select(ScanMode.MCP_AND_BURP);

        assertThat(heard).containsExactly(ScanMode.MCP_AND_BURP);
    }

    @Test
    void aRemovedListenerHearsNothing() {
        List<ScanMode> heard = new ArrayList<>();
        Consumer<ScanMode> listener = heard::add;
        setting.addListener(listener);
        setting.removeListener(listener);

        setting.select(ScanMode.MCP_AND_BURP);

        assertThat(heard).isEmpty();
    }

    @Test
    void aFailingListenerDoesNotStopTheOthersHearingAndItsFailureStillSurfaces() {
        List<ScanMode> heard = new ArrayList<>();
        setting.addListener(mode -> {
            throw new IllegalStateException("listener failed");
        });
        setting.addListener(heard::add);

        assertThatThrownBy(() -> setting.select(ScanMode.MCP_AND_BURP)).hasMessage("listener failed");

        assertThat(heard).containsExactly(ScanMode.MCP_AND_BURP);
        assertThat(setting.current()).isEqualTo(ScanMode.MCP_AND_BURP);
    }
}
