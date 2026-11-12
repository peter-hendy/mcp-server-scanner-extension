package com.mcpscanner.ui;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.client.ConnectResult;
import com.mcpscanner.mcp.McpPromptDefinition;
import com.mcpscanner.mcp.McpResourceDefinition;
import com.mcpscanner.mcp.McpResourceTemplateDefinition;
import com.mcpscanner.mcp.McpToolDefinition;
import com.mcpscanner.mcp.ServerMetadata;
import com.mcpscanner.ui.sendto.SendToDestination;
import com.mcpscanner.core.protocol.McpRequests;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.mcpscanner.ui.McpScannerTabFixture.onEdt;
import static com.mcpscanner.ui.McpScannerTabFixture.prompts;
import static com.mcpscanner.ui.McpScannerTabFixture.resources;
import static com.mcpscanner.ui.McpScannerTabFixture.templates;
import static com.mcpscanner.ui.McpScannerTabFixture.tools;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class McpScannerTabSendToTest {

    private McpScannerTabFixture fixture;
    private final List<String> sentTitles = new ArrayList<>();
    private final List<HttpRequest> sentRequests = new ArrayList<>();

    @BeforeEach
    void setUp() {
        fixture = new McpScannerTabFixture();
        when(fixture.session.scannerHeaders()).thenReturn(Map.of("Mcp-Session-Id", "session-1"));
        when(fixture.session.operation(anyString(), anyString(), anyLong())).thenAnswer(invocation ->
                McpRequests.sse(invocation.getArgument(0), invocation.getArgument(1), invocation.<Long>getArgument(2)));
    }

    @Test
    void sendsEveryInventoryKindWithoutLaunchingScan() throws Exception {
        McpScannerTab tab = connectedTab();
        onEdt(() -> {
            resources(tab).getTableModel().setValueAt(false, 0, 0);
            click(tools(tab).rowMenu(0));
            click(resources(tab).rowMenu(0));
            click(templates(tab).rowMenu(0));
            click(prompts(tab).rowMenu(0));
            assertThat(resources(tab).getTableModel().getValueAt(0, 0)).isEqualTo(false);
        });

        assertThat(sentTitles).hasSize(4);
        assertThat(sentTitles).anyMatch(title -> title.startsWith("Tool:"));
        assertThat(sentTitles).anyMatch(title -> title.startsWith("Resource:"));
        assertThat(sentTitles).anyMatch(title -> title.startsWith("Template:"));
        assertThat(sentTitles).anyMatch(title -> title.startsWith("Prompt:"));
        verify(fixture.scanLauncher, never()).launchScan(any(), any(), any(), any());
    }

    @Test
    void clickedSortedRowIsSentWithoutChangingScanCheckbox() throws Exception {
        McpScannerTab tab = connectedTab();
        onEdt(() -> {
            ToolTablePanel tools = tools(tab);
            tools.populate(List.of(new McpToolDefinition("zeta", "", "{}"),
                    new McpToolDefinition("alpha", "", "{}")));
            tools.table().setAutoCreateRowSorter(true);
            tools.table().getRowSorter().toggleSortOrder(1);
            boolean selected = (boolean) tools.getTableModel().getValueAt(1, 0);
            click(tools.rowMenu(0));
            assertThat(tools.getTableModel().getValueAt(1, 0)).isEqualTo(selected);
        });

        assertThat(sentTitles).containsExactly("Tool: alpha");
    }

    @Test
    void disconnectedMenuCannotSendAnywhere() throws Exception {
        McpScannerTab tab = connectedTab();
        when(fixture.clientManager.isConnected()).thenReturn(false);
        onEdt(() -> {
            JPopupMenu menu = resources(tab).rowMenu(0);
            assertThat(((JMenuItem) menu.getComponent(0)).isEnabled()).isFalse();
            click(menu);
        });

        assertThat(sentTitles).isEmpty();
    }

    private McpScannerTab connectedTab() throws Exception {
        ConnectResult result = new ConnectResult(
                List.of(new McpToolDefinition("echo", "", "{}")),
                List.of(new McpResourceDefinition("file:///a", "a", "", "text/plain")),
                List.of(new McpResourceTemplateDefinition("file:///{path}", "files", "", "text/plain")),
                List.of(new McpPromptDefinition("summary", "", List.of())), ServerMetadata.empty());
        McpScannerTab tab = fixture.connected(result);
        onEdt(() -> tab.setSendToDestinations(List.of(SendToDestination.of("Repeater", (request, title) -> {
            sentRequests.add(request);
            sentTitles.add(title);
        }))));
        return tab;
    }

    private void click(JPopupMenu menu) {
        ((JMenuItem) menu.getComponent(0)).doClick();
    }
}
