package com.mcpscanner.cvetargets.run;

import com.mcpscanner.cvetargets.manifest.TargetSelection;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class HarnessSettingsTest {

    @Test
    void keepsTheRecorderLocksBesideTheMachineSlots() {
        HarnessSettings settings = new HarnessSettings(Path.of("targets"), Path.of("results"),
                TargetSelection.everything(), 2, Path.of("/cache/mcp-cve-targets/slots"), 2, false);

        assertThat(settings.recorderLocksDirectory()).isEqualTo(Path.of("/cache/mcp-cve-targets/recorder"));
    }
}
