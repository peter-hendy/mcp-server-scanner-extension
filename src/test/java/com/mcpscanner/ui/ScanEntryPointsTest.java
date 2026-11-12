package com.mcpscanner.ui;

import com.mcpscanner.scan.ScanMode;
import com.mcpscanner.client.ConnectResult;
import com.mcpscanner.mcp.McpPromptDefinition;
import com.mcpscanner.mcp.McpResourceDefinition;
import com.mcpscanner.mcp.McpResourceTemplateDefinition;
import com.mcpscanner.mcp.McpToolDefinition;
import com.mcpscanner.mcp.PromptArgument;
import com.mcpscanner.mcp.ServerMetadata;
import com.mcpscanner.mcp.ToolAnnotations;
import com.mcpscanner.scan.request.GeneratedScan;
import com.mcpscanner.scan.request.McpMessage;
import com.mcpscanner.scan.request.RequestFamily;
import com.mcpscanner.scan.request.ScanMessage;
import com.mcpscanner.testutil.SwingTree;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import javax.swing.JCheckBox;
import javax.swing.JTable;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.mcpscanner.ui.McpScannerTabFixture.click;
import static com.mcpscanner.ui.McpScannerTabFixture.onEdt;
import static com.mcpscanner.ui.McpScannerTabFixture.prompts;
import static com.mcpscanner.ui.McpScannerTabFixture.resources;
import static com.mcpscanner.ui.McpScannerTabFixture.scanButton;
import static com.mcpscanner.ui.McpScannerTabFixture.templates;
import static com.mcpscanner.ui.McpScannerTabFixture.tools;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScanEntryPointsTest {

    private static final Map<String, Object> ALL_CAPABILITIES = Map.of(
            "tools", Map.of(), "resources", Map.of("subscribe", true), "prompts", Map.of(),
            "completions", Map.of(), "logging", Map.of());

    private static final McpToolDefinition READ_ONLY_TOOL = new McpToolDefinition("lookup", "", "{}", List.of(),
            new ToolAnnotations(null, true, null, null, null));
    private static final McpToolDefinition DESTRUCTIVE_TOOL = new McpToolDefinition("wipe", "", "{}", List.of(),
            new ToolAnnotations(null, false, true, null, null));
    private static final McpToolDefinition OWNED_DOCUMENT_TOOL = new McpToolDefinition("get_document", "",
            "{\"type\":\"object\",\"properties\":{\"doc_id\":{\"type\":\"string\"}},\"required\":[\"doc_id\"]}",
            List.of(), new ToolAnnotations(null, true, null, null, null));
    private static final McpToolDefinition DELETE_DOCUMENT_TOOL = new McpToolDefinition("delete_document", "",
            "{\"type\":\"object\",\"properties\":{\"doc_id\":{\"type\":\"string\"}},\"required\":[\"doc_id\"]}");
    private static final McpResourceDefinition RESOURCE =
            new McpResourceDefinition("file:///a.txt", "a", "", "text/plain", List.of());
    private static final McpResourceTemplateDefinition TEMPLATE =
            new McpResourceTemplateDefinition("file:///docs/{name}", "docs", "", "text/plain");
    private static final McpPromptDefinition PROMPT = new McpPromptDefinition("greet", "",
            List.of(new PromptArgument("who", "", true)), List.of());
    private static final ConnectResult INVENTORY = new ConnectResult(List.of(READ_ONLY_TOOL, DESTRUCTIVE_TOOL),
            List.of(RESOURCE), List.of(TEMPLATE), List.of(PROMPT),
            new ServerMetadata(Map.of("name", "fixture"), null, ALL_CAPABILITIES));

    private final McpScannerTabFixture fixture = new McpScannerTabFixture();
    private final List<GeneratedScan> launched = new CopyOnWriteArrayList<>();
    private final List<Map<String, String>> launchedHeaders = new CopyOnWriteArrayList<>();
    private final List<List<McpToolDefinition>> confirmationPrompts = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() {
        when(fixture.session.scannerHeaders())
                .thenReturn(Map.of("Mcp-Session-Id", "live", "MCP-Protocol-Version", "2024-11-05"));
        fixture.scanPrompt = (parent, tools, count, invoker) -> {
            confirmationPrompts.add(tools);
            return new DestructiveScanConfirmation.Result(true, false);
        };
        doAnswer(call -> {
            launched.add(call.getArgument(2));
            launchedHeaders.add(call.getArgument(3));
            return null;
        }).when(fixture.scanLauncher).launchScan(any(ScanMode.class), anyString(), any(GeneratedScan.class), anyMap());
    }

    @Test
    void scanSendsEveryFamilyTheServerSupports() throws Exception {
        McpScannerTab tab = connectedWithEverythingTicked();
        onEdt(() -> click(scanButton(tab)));

        assertThat(families(awaitLaunch())).isEqualTo(EnumSet.complementOf(EnumSet.of(RequestFamily.CLIENT_REGISTRATION)));
    }

    @ParameterizedTest
    @EnumSource(value = RequestFamily.class, names = "CLIENT_REGISTRATION", mode = EnumSource.Mode.EXCLUDE)
    void scanOmitsAFamilySwitchedOffInSettings(RequestFamily off) throws Exception {
        fixture.generationSettings.setEnabled(off, false);
        McpScannerTab tab = connectedWithEverythingTicked();
        onEdt(() -> click(scanButton(tab)));

        assertThat(families(awaitLaunch())).doesNotContain(off).contains(anyOtherThan(off));
    }

    @Test
    void theRequestsPanelCheckboxSwitchesAFamilyOffForTheNextScan() throws Exception {
        McpScannerTab tab = connectedWithEverythingTicked();
        onEdt(() -> {
            familyBox(tab, RequestFamily.PING).doClick();
            click(scanButton(tab));
        });

        assertThat(families(awaitLaunch())).doesNotContain(RequestFamily.PING);
    }

    @Test
    void scanAllCallsToolsWithTheConnectAsUsersOwnedObjects() throws Exception {
        fixture.scanOwnerArguments = Map.of("doc_id", "d-alice-0001");
        McpScannerTab tab = fixture.connected(new ConnectResult(List.of(OWNED_DOCUMENT_TOOL), List.of(), List.of(),
                List.of(), new ServerMetadata(Map.of("name", "fixture"), null, ALL_CAPABILITIES)));
        onEdt(() -> {
            tickEveryRow(tools(tab).table());
            click(scanButton(tab));
        });

        McpMessage call = (McpMessage) awaitLaunch().messages().stream()
                .filter(message -> message.family() == RequestFamily.TOOLS_CALL).findFirst().orElseThrow();
        assertThat(call.params().orElseThrow().path("arguments").path("doc_id").asText()).isEqualTo("d-alice-0001");
    }

    @Test
    void scanCarriesTheLiveSessionHeadersToTheLauncher() throws Exception {
        McpScannerTab tab = connectedWithEverythingTicked();
        onEdt(() -> click(scanButton(tab)));
        awaitLaunch();

        assertThat(launchedHeaders.get(0))
                .containsEntry("Mcp-Session-Id", "live").containsEntry("MCP-Protocol-Version", "2024-11-05");
    }

    @Test
    void scanAsksForConfirmationOverEveryNonReadOnlyToolItWillCall() throws Exception {
        McpScannerTab tab = connectedWithEverythingTicked();
        onEdt(() -> click(scanButton(tab)));
        awaitLaunch();

        assertThat(confirmationPrompts).containsExactly(List.of(DESTRUCTIVE_TOOL));
    }

    @Test
    void scanningOneToolSendsExactlyThatToolCallEvenWhenItsFamilyIsSwitchedOff() throws Exception {
        fixture.generationSettings.setEnabled(RequestFamily.TOOLS_CALL, false);
        McpScannerTab tab = fixture.connected(INVENTORY);
        onEdt(() -> scanSelectedRow(tools(tab).table(), tools(tab).detailPanel().header(), 0));

        assertThat(subjects(awaitLaunch())).containsExactly("tools/call lookup");
        assertThat(confirmationPrompts).isEmpty();
    }

    @Test
    void scanningOneResourceSendsExactlyThatRead() throws Exception {
        McpScannerTab tab = fixture.connected(INVENTORY);
        onEdt(() -> scanSelectedRow(resources(tab).table(), resources(tab).detailPanel().header(), 0));

        assertThat(subjects(awaitLaunch())).containsExactly("resources/read file:///a.txt");
    }

    @Test
    void scanningOneTemplateSendsExactlyThatExpandedRead() throws Exception {
        McpScannerTab tab = fixture.connected(INVENTORY);
        onEdt(() -> scanSelectedRow(templates(tab).table(), templates(tab).detailPanel().header(), 0));

        GeneratedScan scan = awaitLaunch();
        assertThat(subjects(scan)).containsExactly("resources/read file:///docs/name");
        assertThat(scan.messages().get(0).family()).isEqualTo(RequestFamily.RESOURCE_TEMPLATES_READ);
    }

    @Test
    void scanningOnePromptSendsExactlyThatPromptGet() throws Exception {
        McpScannerTab tab = fixture.connected(INVENTORY);
        onEdt(() -> scanSelectedRow(prompts(tab).table(), prompts(tab).detailPanel().header(), 0));

        assertThat(subjects(awaitLaunch())).containsExactly("prompts/get greet");
    }

    @Test
    void scanningOneDestructiveToolNeedsConfirmationAndStopsWhenDeclined() throws Exception {
        fixture.scanPrompt = (parent, tools, count, invoker) -> {
            confirmationPrompts.add(tools);
            return new DestructiveScanConfirmation.Result(false, false);
        };
        McpScannerTab tab = fixture.connected(INVENTORY);
        onEdt(() -> scanSelectedRow(tools(tab).table(), tools(tab).detailPanel().header(), 1));
        McpScannerTabFixture.flushEdt();

        assertThat(confirmationPrompts).containsExactly(List.of(DESTRUCTIVE_TOOL));
        verify(fixture.scanLauncher, never()).launchScan(any(ScanMode.class), anyString(), any(GeneratedScan.class), anyMap());
    }

    @Test
    void scanningAWriteToolThatCarriesMinedValuesAsksEvenAfterDontAskAgain() throws Exception {
        fixture.configStore.setDontAskAgain(McpScannerTabFixture.ENDPOINT, true);
        fixture.scanOwnerArguments = Map.of("doc_id", "d-alice-0001");
        fixture.scanMinedArguments = java.util.Set.of("doc_id");
        List<DestructiveScanConfirmation.Lines> asked = new CopyOnWriteArrayList<>();
        fixture.minedWritePrompt = (parent, lines) -> {
            asked.add(lines);
            return new DestructiveScanConfirmation.Result(false, false);
        };
        McpScannerTab tab = fixture.connected(new ConnectResult(List.of(DELETE_DOCUMENT_TOOL), List.of(), List.of(),
                List.of(), new ServerMetadata(Map.of("name", "fixture"), null, ALL_CAPABILITIES)));
        onEdt(() -> scanSelectedRow(tools(tab).table(), tools(tab).detailPanel().header(), 0));
        McpScannerTabFixture.flushEdt();

        assertThat(asked).singleElement().satisfies(lines -> assertThat(lines.items())
                .anyMatch(item -> item.contains("doc_id=d-alice-0001 (collected)")));
        verify(fixture.scanLauncher, never()).launchScan(any(ScanMode.class), anyString(), any(GeneratedScan.class), anyMap());
    }

    private McpScannerTab connectedWithEverythingTicked() throws Exception {
        McpScannerTab tab = fixture.connected(INVENTORY);
        onEdt(() -> List.of(tools(tab).table(), resources(tab).table(), templates(tab).table(), prompts(tab).table())
                .forEach(ScanEntryPointsTest::tickEveryRow));
        return tab;
    }

    private static void tickEveryRow(JTable table) {
        for (int row = 0; row < table.getModel().getRowCount(); row++) {
            table.getModel().setValueAt(true, row, 0);
        }
    }

    private static void scanSelectedRow(JTable table, DetailHeader<?> header, int row) {
        table.setRowSelectionInterval(row, row);
        header.scanButton().doClick();
    }

    private static JCheckBox familyBox(McpScannerTab tab, RequestFamily family) {
        return SwingTree.descendants(tab).stream()
                .filter(JCheckBox.class::isInstance).map(JCheckBox.class::cast)
                .filter(box -> family.label().equals(box.getText()))
                .findFirst().orElseThrow();
    }

    private GeneratedScan awaitLaunch() {
        await().atMost(5, TimeUnit.SECONDS).until(() -> !launched.isEmpty());
        return launched.get(0);
    }

    private static Set<RequestFamily> families(GeneratedScan scan) {
        return scan.messages().stream().map(ScanMessage::family)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(RequestFamily.class)));
    }

    private static List<String> subjects(GeneratedScan scan) {
        return scan.messages().stream().map(ScanMessage::subject).toList();
    }

    private static RequestFamily anyOtherThan(RequestFamily off) {
        return off == RequestFamily.PING ? RequestFamily.TOOLS_CALL : RequestFamily.PING;
    }
}
