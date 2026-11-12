package com.mcpscanner.checks.registry;

import com.mcpscanner.checks.api.RecordingChecks;
import com.mcpscanner.logging.McpEventLog;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class McpCheckAdaptersTest {

    private final McpCheckAdapters adapters =
            new McpCheckAdapters(AdapterFixtures.settingsAllowing(true), McpEventLog.noop());

    @Test
    void insertionPointCheckIsAdaptedAsAnInsertionPointAudit() {
        assertThat(adapters.adapt(new RecordingChecks.InsertionPoint("ip", List.of())))
                .isInstanceOf(InsertionPointCheckAdapter.class);
    }

    @Test
    void requestCheckIsAdaptedAsAPerRequestAudit() {
        assertThat(adapters.adapt(new RecordingChecks.Request("r", List.of())))
                .isInstanceOf(RequestCheckAdapter.class);
    }

    @Test
    void hostCheckIsAdaptedAsAPerHostAudit() {
        assertThat(adapters.adapt(new RecordingChecks.Host("h", List.of())))
                .isInstanceOf(HostCheckAdapter.class);
    }

    @Test
    void adaptedCheckKeepsTheWrappedDescriptor() {
        RecordingChecks.Host check = new RecordingChecks.Host("h", List.of());

        assertThat(adapters.adapt(check).descriptor()).isSameAs(check.descriptor());
    }

    @Test
    void rejectsMissingSettings() {
        assertThatThrownBy(() -> new McpCheckAdapters(null, McpEventLog.noop()))
                .isInstanceOf(NullPointerException.class);
    }
}
