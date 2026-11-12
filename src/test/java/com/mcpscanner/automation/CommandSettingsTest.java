package com.mcpscanner.automation;

import com.mcpscanner.checks.api.CheckDescriptor;
import com.mcpscanner.checks.registry.ManagedCheck;
import com.mcpscanner.checks.registry.ScanCheckRegistry;
import com.mcpscanner.checks.registry.ScanCheckSettings;
import com.mcpscanner.testutil.InMemoryPersistedObject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CommandSettingsTest {

    private final ScanCheckRegistry registry = mock(ScanCheckRegistry.class);
    private final ScanCheckSettings checkSettings = new ScanCheckSettings(InMemoryPersistedObject.configStore());
    private final List<Boolean> diversion = new ArrayList<>();
    private final CommandSettings settings = new CommandSettings(registry, checkSettings, diversion::add);

    @Test
    void enablesOnlyTheRequestedChecks() {
        List<ManagedCheck> catalog = List.of(check("file-traversal"), check("outbound-request"));
        when(registry.all()).thenReturn(catalog);

        settings.apply(command(Set.of("file-traversal"), null));

        assertThat(checkSettings.isEnabled("file-traversal", false)).isTrue();
        assertThat(checkSettings.isEnabled("outbound-request", true)).isFalse();
    }

    @Test
    void enablesEveryCatalogCheckWhenAskedForAll() {
        List<ManagedCheck> catalog = List.of(check("file-traversal"), check("outbound-request"));
        when(registry.all()).thenReturn(catalog);

        settings.apply(command(Set.of(ScanAllCommand.EVERY_CHECK), null));

        assertThat(checkSettings.isEnabled("file-traversal", false)).isTrue();
        assertThat(checkSettings.isEnabled("outbound-request", false)).isTrue();
    }

    @Test
    void rejectsACheckTheCatalogDoesNotHave() {
        List<ManagedCheck> catalog = List.of(check("file-traversal"));
        when(registry.all()).thenReturn(catalog);

        assertThatThrownBy(() -> settings.apply(command(Set.of("no-such-check"), null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no-such-check");
    }

    @Test
    void setsBuiltInCheckDiversionOnEveryLaunch() {
        when(registry.all()).thenReturn(List.of());

        settings.apply(new ScanAllCommand("http://127.0.0.1:1/mcp", null, null, Set.of(), null, null, true, null));
        settings.apply(command(Set.of(), null));

        assertThat(diversion).containsExactly(true, false);
    }

    private static ScanAllCommand command(Set<String> checks, Boolean useBurpNetworking) {
        return new ScanAllCommand("http://127.0.0.1:1/mcp", null, null, checks, null, useBurpNetworking, false, null);
    }

    private static ManagedCheck check(String id) {
        ManagedCheck check = mock(ManagedCheck.class);
        CheckDescriptor descriptor = mock(CheckDescriptor.class);
        when(descriptor.id()).thenReturn(id);
        when(check.descriptor()).thenReturn(descriptor);
        return check;
    }
}
