package com.mcpscanner.ui;

import com.mcpscanner.config.ExtensionConfigStore;
import com.mcpscanner.mcp.McpToolDefinition;
import com.mcpscanner.mcp.ToolAnnotations;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ToolSelectionPreferencesTest {

    private static final String ENDPOINT = "https://mcp.example.com/mcp";

    private final ExtensionConfigStore store = mock(ExtensionConfigStore.class);
    private final McpToolDefinition readOnlyTool =
            new McpToolDefinition("read", "", "{}", List.of(), new ToolAnnotations(null, true, null, null, null));

    @Test
    void rememberStoresTheTickAgainstTheActiveEndpoint() {
        new ToolSelectionPreferences(store, () -> ENDPOINT).remember(readOnlyTool, false);

        verify(store).setToolSelected(ENDPOINT, "read", false);
    }

    @Test
    void rememberIsANoOpWithoutAnEndpoint() {
        new ToolSelectionPreferences(store, () -> "").remember(readOnlyTool, true);

        verify(store, never()).setToolSelected(anyString(), anyString(), anyBoolean());
    }

    @Test
    void defaultSelectionPrefersTheStoredTick() {
        when(store.toolSelected(ENDPOINT, "read")).thenReturn(false);

        assertThat(new ToolSelectionPreferences(store, () -> ENDPOINT).defaultSelection(readOnlyTool)).isFalse();
    }

    @Test
    void defaultSelectionFallsBackToTheReadOnlyAnnotationWithoutAnEndpoint() {
        assertThat(new ToolSelectionPreferences(store, () -> null).defaultSelection(readOnlyTool)).isTrue();
    }

    @Test
    void anUnconfiguredPreferenceIsNotConfigured() {
        assertThat(ToolSelectionPreferences.none().isConfigured()).isFalse();
    }
}
