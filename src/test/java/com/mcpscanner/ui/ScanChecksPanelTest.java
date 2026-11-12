package com.mcpscanner.ui;

import burp.api.montoya.scanner.audit.issues.AuditIssueSeverity;
import com.mcpscanner.checks.registry.ScanCheckRegistry;
import com.mcpscanner.checks.api.CheckCatalog;
import com.mcpscanner.checks.api.CheckServices;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.checks.registry.ScanCheckSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JTable;
import javax.swing.RowSorter;
import javax.swing.SortOrder;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ScanChecksPanelTest {

    private ScanCheckSettings settings;
    private ScanCheckRegistry registry;

    @BeforeEach
    void setUp() {
        settings = mock(ScanCheckSettings.class);
        when(settings.isEnabled(anyString(), anyBoolean())).thenReturn(true);
        registry = new ScanCheckRegistry(CheckCatalog.discover(CheckServices.standalone()),
                settings, McpEventLog.noop());
    }

    @Test
    void theDescriptionSitsBelowTheChecksSoTheyFitANarrowSidePanel() {
        ScanChecksPanel panel = new ScanChecksPanel(registry, settings);

        javax.swing.JSplitPane split = (javax.swing.JSplitPane) com.mcpscanner.testutil.SwingTree.descendants(panel)
                .stream().filter(javax.swing.JSplitPane.class::isInstance).findFirst().orElseThrow();

        assertThat(split.getOrientation()).isEqualTo(javax.swing.JSplitPane.VERTICAL_SPLIT);
        assertThat(com.mcpscanner.testutil.SwingTree.descendants((java.awt.Container) split.getTopComponent()))
                .anyMatch(JTable.class::isInstance);
    }

    @Test
    void thereAreNoAllOrNoneButtons() {
        ScanChecksPanel panel = new ScanChecksPanel(registry, settings);

        assertThat(com.mcpscanner.testutil.SwingTree.descendants(panel))
                .noneMatch(c -> c instanceof javax.swing.JButton b
                        && ("All".equals(b.getText()) || "None".equals(b.getText())));
    }

    @Test
    void theOnColumnHeaderTicksAndUnticksEveryCheckAndShowsAPartialState() {
        java.util.Map<String, Boolean> enabled = new java.util.HashMap<>();
        when(settings.isEnabled(anyString(), anyBoolean()))
                .thenAnswer(call -> enabled.getOrDefault(call.<String>getArgument(0), true));
        org.mockito.Mockito.doAnswer(call -> enabled.put(call.getArgument(0), call.getArgument(1)))
                .when(settings).setEnabled(anyString(), anyBoolean());
        ScanChecksPanel panel = new ScanChecksPanel(registry, settings);
        assertThat(panel.selectAllHeader().state()).isEqualTo(SelectAllHeader.State.ALL);

        panel.selectAllHeader().toggle();

        assertThat(panel.selectAllHeader().state()).isEqualTo(SelectAllHeader.State.NONE);
        registry.all().forEach(check -> assertThat(enabled.get(check.descriptor().id())).isFalse());

        table(panel).getModel().setValueAt(true, 0, ScanChecksTableModel.COLUMN_ENABLED);

        assertThat(panel.selectAllHeader().state()).isEqualTo(SelectAllHeader.State.PARTIAL);

        panel.selectAllHeader().toggle();

        assertThat(panel.selectAllHeader().state()).isEqualTo(SelectAllHeader.State.ALL);
        registry.all().forEach(check -> assertThat(enabled.get(check.descriptor().id())).isTrue());
    }

    @Test
    void defaultSortPlacesHighestSeverityFirst() {
        ScanChecksPanel panel = new ScanChecksPanel(registry, settings);
        JTable table = table(panel);

        AuditIssueSeverity first = severityAtView(table, 0);

        assertThat(first).isEqualTo(AuditIssueSeverity.HIGH);
    }

    @Test
    void severityColumnSortKeyIsDescendingByDefault() {
        ScanChecksPanel panel = new ScanChecksPanel(registry, settings);
        JTable table = table(panel);

        List<? extends RowSorter.SortKey> keys = table.getRowSorter().getSortKeys();

        assertThat(keys).hasSize(1);
        assertThat(keys.get(0).getColumn()).isEqualTo(ScanChecksTableModel.COLUMN_SEVERITY);
        assertThat(keys.get(0).getSortOrder()).isEqualTo(SortOrder.DESCENDING);
    }

    @Test
    void clickingSeverityHeaderReversesSort() {
        ScanChecksPanel panel = new ScanChecksPanel(registry, settings);
        JTable table = table(panel);

        table.getRowSorter().toggleSortOrder(ScanChecksTableModel.COLUMN_SEVERITY);

        assertThat(table.getRowSorter().getSortKeys().get(0).getSortOrder())
                .isEqualTo(SortOrder.ASCENDING);
        AuditIssueSeverity first = severityAtView(table, 0);
        assertThat(first).isIn(AuditIssueSeverity.INFORMATION, AuditIssueSeverity.LOW,
                AuditIssueSeverity.MEDIUM);
    }

    @Test
    void severityColumnIsNonAscendingTopToBottom() {
        ScanChecksPanel panel = new ScanChecksPanel(registry, settings);
        JTable table = table(panel);

        for (int row = 1; row < table.getRowCount(); row++) {
            int previous = rank(severityAtView(table, row - 1));
            int current = rank(severityAtView(table, row));
            assertThat(current).isLessThanOrEqualTo(previous);
        }
    }

    private static AuditIssueSeverity severityAtView(JTable table, int viewRow) {
        return (AuditIssueSeverity) table.getValueAt(viewRow, ScanChecksTableModel.COLUMN_SEVERITY);
    }

    private static int rank(AuditIssueSeverity severity) {
        return switch (severity) {
            case HIGH -> 4;
            case MEDIUM -> 3;
            case LOW -> 2;
            case INFORMATION -> 1;
            case FALSE_POSITIVE -> 0;
        };
    }

    private static JTable table(ScanChecksPanel panel) {
        return com.mcpscanner.testutil.SwingTree.descendants(panel).stream()
                .filter(JTable.class::isInstance).map(JTable.class::cast).findFirst().orElseThrow();
    }

    @Test
    void disposeDetachesTheSettingsListenerItRegistered() {
        ScanChecksPanel panel = new ScanChecksPanel(registry, settings);
        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<java.util.function.BiConsumer<String, Boolean>> added =
                org.mockito.ArgumentCaptor.forClass(java.util.function.BiConsumer.class);
        org.mockito.Mockito.verify(settings).addListener(added.capture());

        panel.dispose();

        org.mockito.Mockito.verify(settings).removeListener(added.getValue());
    }
}
