package com.mcpscanner.ui;

import com.mcpscanner.client.ConnectResult;
import com.mcpscanner.mcp.McpPromptDefinition;
import com.mcpscanner.mcp.McpResourceDefinition;
import com.mcpscanner.mcp.McpResourceTemplateDefinition;
import com.mcpscanner.mcp.McpToolDefinition;
import com.mcpscanner.mcp.ServerMetadata;
import com.mcpscanner.scan.RunningScan;
import com.mcpscanner.scan.ScanInventory;
import com.mcpscanner.scan.ScanMode;
import com.mcpscanner.scan.request.GeneratedScan;
import com.mcpscanner.scan.request.RequestFamily;
import com.mcpscanner.scan.request.ScanMessage;
import com.mcpscanner.ui.sendto.SendToDestination;
import com.mcpscanner.ui.sendto.SendToShortcuts;
import com.mcpscanner.core.protocol.McpRequests;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static com.mcpscanner.ui.McpScannerTabFixture.onEdt;
import static com.mcpscanner.ui.McpScannerTabFixture.prompts;
import static com.mcpscanner.ui.McpScannerTabFixture.resources;
import static com.mcpscanner.ui.McpScannerTabFixture.templates;
import static com.mcpscanner.ui.McpScannerTabFixture.tools;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class McpScannerTabItemActionsTest {

    private final McpToolDefinition tool = new McpToolDefinition("echo", "", "{}");
    private final McpResourceDefinition resource = new McpResourceDefinition("file:///a", "a", "", "text/plain");
    private final McpResourceTemplateDefinition template =
            new McpResourceTemplateDefinition("file:///{path}", "files", "", "text/plain");
    private final McpPromptDefinition prompt = new McpPromptDefinition("summary", "", List.of());
    private final List<String> sentTitles = new ArrayList<>();
    private final AtomicReference<GeneratedScan> scanned = new AtomicReference<>();
    private McpScannerTabFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new McpScannerTabFixture();
        when(fixture.scanLauncher.captureScan(any(ScanMode.class), anyString(), any(GeneratedScan.class), any(), any()))
                .thenAnswer(call -> {
                    scanned.set(call.getArgument(2));
                    return (Supplier<RunningScan>) () -> null;
                });
        when(fixture.session.operation(anyString(), anyString(), anyLong())).thenAnswer(invocation ->
                McpRequests.sse(invocation.getArgument(0), invocation.getArgument(1), invocation.<Long>getArgument(2)));
    }

    @Test
    void theRepeaterHotkeySendsTheSelectedRowOfEveryInventoryKind() throws Exception {
        McpScannerTab tab = connectedTab();
        onEdt(() -> {
            pressRepeaterHotkey(tools(tab).view(), tools(tab).table());
            pressRepeaterHotkey(resources(tab).view(), resources(tab).table());
            pressRepeaterHotkey(templates(tab).view(), templates(tab).table());
            pressRepeaterHotkey(prompts(tab).view(), prompts(tab).table());
        });

        assertThat(sentTitles).containsExactly("Tool: echo", "Resource: a", "Template: files", "Prompt: summary");
        verify(fixture.scanLauncher, never()).launchScan(any(), any(), any(), any());
    }

    @Test
    void theHotkeyCoversTheDetailPaneAsWellAsTheTable() throws Exception {
        McpScannerTab tab = connectedTab();
        onEdt(() -> {
            ResourceTablePanel resources = resources(tab);
            select(resources.table());
            InventoryView view = resources.view();
            assertThat(SwingUtilities.isDescendingFrom(resources.detailPanel().component(), view)).isTrue();
        });
    }

    @Test
    void theHotkeyDoesNothingWithoutASelectedRow() throws Exception {
        McpScannerTab tab = connectedTab();
        onEdt(() -> invoke(tools(tab).view(), repeaterKey()));

        assertThat(sentTitles).isEmpty();
    }

    @Test
    void eachDetailHeaderOffersAScanForJustThatKindOfItem() throws Exception {
        McpScannerTab tab = connectedTab();
        onEdt(() -> {
            assertThat(scanButton(tools(tab).detailPanel().header()).getText()).isEqualTo("Scan this tool");
            assertThat(scanButton(resources(tab).detailPanel().header()).getText()).isEqualTo("Scan this resource");
            assertThat(scanButton(templates(tab).detailPanel().header()).getText()).isEqualTo("Scan this template");
            assertThat(scanButton(prompts(tab).detailPanel().header()).getText()).isEqualTo("Scan this prompt");
        });
    }

    @Test
    void scanThisResourceScansOnlyThatResourceThroughTheScanLauncher() throws Exception {
        McpScannerTab tab = connectedTab();
        onEdt(() -> {
            select(resources(tab).table());
            JButton scan = scanButton(resources(tab).detailPanel().header());
            assertThat(scan.isEnabled()).isTrue();
            assertThat(((com.mcpscanner.ui.widgets.PrimaryActionButton) scan).isPrimary()).isTrue();
            scan.doClick();
        });

        assertThat(scanned.get().messages()).extracting(ScanMessage::family, ScanMessage::subject)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(RequestFamily.RESOURCES_READ,
                        "resources/read " + resource.uri()));
    }

    @Test
    void scanThisToolAsksTheSameUnsafeToolQuestionAsTheScanBar() throws Exception {
        List<List<McpToolDefinition>> asked = new ArrayList<>();
        McpToolDefinition destructive = new McpToolDefinition("destroy", "", "{}");
        fixture.scanPrompt = (parent, tools, count, invoker) -> {
            asked.add(tools);
            return new DestructiveScanConfirmation.Result(false, false);
        };
        McpScannerTab tab = connectedTab();
        onEdt(() -> {
            tools(tab).populate(List.of(destructive));
            select(tools(tab).table());
            scanButton(tools(tab).detailPanel().header()).doClick();
        });

        assertThat(asked).containsExactly(List.of(destructive));
        assertThat(scanned.get().invokedTools()).containsExactly(destructive);
        verify(fixture.scanLauncher, never()).launchScan(any(), any(), any(), any());
    }

    @Test
    void scanThisItemTooltipNamesTheScanModeAndFollowsItsChanges() throws Exception {
        McpScannerTab tab = connectedTab();
        onEdt(() -> {
            select(tools(tab).table());
            assertThat(scanButton(tools(tab).detailPanel().header()).getToolTipText())
                    .isEqualTo(scanOnlyThisTool(ScanMode.MCP_ONLY));
        });

        fixture.scanModeSetting.select(ScanMode.MCP_AND_BURP);
        McpScannerTabFixture.flushEdt();

        onEdt(() -> assertThat(scanButton(tools(tab).detailPanel().header()).getToolTipText())
                .isEqualTo(scanOnlyThisTool(ScanMode.MCP_AND_BURP)));
    }

    private static String scanOnlyThisTool(ScanMode mode) {
        return "Scan only this tool with " + ScanModeText.of(mode).label();
    }

    @Test
    void scanThisItemIsDisabledWithTheScanBarReasonWhenDisconnected() throws Exception {
        McpScannerTab tab = connectedTab();
        when(fixture.clientManager.isConnected()).thenReturn(false);
        onEdt(() -> {
            select(prompts(tab).table());
            JButton scan = scanButton(prompts(tab).detailPanel().header());
            assertThat(scan.isEnabled()).isFalse();
            assertThat(scan.getToolTipText()).isEqualTo("Connect to an MCP server first");
        });
    }

    private McpScannerTab connectedTab() throws Exception {
        return fixture.connected(new ConnectResult(List.of(tool), List.of(resource), List.of(template),
                List.of(prompt), ServerMetadata.empty()), tab -> {
                    tab.setSendToShortcuts(SendToShortcuts.defaults());
                    tab.setSendToDestinations(List.of(SendToDestination.of("Repeater",
                            (request, title) -> sentTitles.add(title))));
                });
    }

    private static void pressRepeaterHotkey(JComponent scope, JTable table) {
        select(table);
        invoke(scope, repeaterKey());
    }

    private static void select(JTable table) {
        table.setRowSelectionInterval(0, 0);
    }

    private static KeyStroke repeaterKey() {
        return SendToShortcuts.defaults().keyStrokeFor("Repeater").orElseThrow();
    }

    private static void invoke(JComponent scope, KeyStroke key) {
        Object name = scope.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).get(key);
        assertThat(name).as("binding for " + key).isNotNull();
        scope.getActionMap().get(name).actionPerformed(new ActionEvent(scope, ActionEvent.ACTION_PERFORMED, ""));
    }

    private static JButton scanButton(DetailHeader<?> header) {
        return header.scanButton();
    }
}
