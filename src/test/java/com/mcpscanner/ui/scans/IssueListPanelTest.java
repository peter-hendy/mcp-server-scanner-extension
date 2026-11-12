package com.mcpscanner.ui.scans;

import burp.api.montoya.core.Marker;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.scanner.audit.issues.AuditIssue;
import burp.api.montoya.scanner.audit.issues.AuditIssueConfidence;
import burp.api.montoya.scanner.audit.issues.AuditIssueSeverity;
import com.mcpscanner.engine.ScanFindings;
import com.mcpscanner.testutil.MontoyaTestFactory;
import com.mcpscanner.ui.widgets.DotIcon;
import com.mcpscanner.ui.widgets.SeverityColors;
import org.junit.jupiter.api.Test;

import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static com.mcpscanner.testutil.SwingTree.descendants;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class IssueListPanelTest {

    private final AuditIssue high = Issues.issue("OS command injection", AuditIssueSeverity.HIGH);
    private final AuditIssue info = Issues.issue("Session ID guessable", AuditIssueSeverity.INFORMATION);
    private final List<AuditIssue> selected = new ArrayList<>();
    private final IssueListPanel panel = new IssueListPanel(selected::add);

    @Test
    void listsSeverityIssueAndWhatWasTestedAtFullLengthWithoutAUrlOrPerRowLink() {
        panel.show(findings(high, info));

        JTable table = table();
        assertThat(IntStream.range(0, table.getColumnCount()).mapToObj(table::getColumnName))
                .containsExactly("Severity", "Issue", "Tested");
        assertThat(table.getValueAt(0, 0)).isEqualTo("High");
        assertThat(table.getValueAt(0, 1)).isEqualTo("OS command injection");
        assertThat(SwingUtilities.getAncestorOfClass(JScrollPane.class, table)).isNull();
        assertThat(descendants(panel)).noneMatch(JScrollPane.class::isInstance);
    }

    @Test
    void sortsBySeverityThenConfidenceThenName() {
        AuditIssue lowB = rated("B low", AuditIssueSeverity.LOW, AuditIssueConfidence.FIRM);
        AuditIssue highTentative = rated("A high tentative", AuditIssueSeverity.HIGH, AuditIssueConfidence.TENTATIVE);
        AuditIssue highCertain = rated("Z high certain", AuditIssueSeverity.HIGH, AuditIssueConfidence.CERTAIN);
        AuditIssue highFirmB = rated("B high firm", AuditIssueSeverity.HIGH, AuditIssueConfidence.FIRM);
        AuditIssue highFirmA = rated("A high firm", AuditIssueSeverity.HIGH, AuditIssueConfidence.FIRM);
        AuditIssue medium = rated("Medium", AuditIssueSeverity.MEDIUM, AuditIssueConfidence.CERTAIN);

        panel.show(findings(info, lowB, highTentative, medium, highCertain, highFirmB, highFirmA));

        assertThat(IntStream.range(0, table().getRowCount()).mapToObj(row -> table().getValueAt(row, 1)))
                .containsExactly("Z high certain", "A high firm", "B high firm", "A high tentative", "Medium",
                        "B low", "Session ID guessable");
    }

    @Test
    void eachRowShowsAColouredSeverityDotWithTheSeverityAsTextAndTooltip() {
        panel.show(findings(high, info));

        JLabel highCell = renderedSeverity(0);
        assertThat(highCell.getText()).isEqualTo("High");
        assertThat(((DotIcon) highCell.getIcon()).color()).isEqualTo(SeverityColors.of(AuditIssueSeverity.HIGH).get());
        assertThat(highCell.getToolTipText()).isEqualTo("High severity, Firm confidence");
        JLabel infoCell = renderedSeverity(1);
        assertThat(((DotIcon) infoCell.getIcon()).color())
                .isEqualTo(SeverityColors.of(AuditIssueSeverity.INFORMATION).get());
    }

    @Test
    void selectingARowShowsItsDetail() {
        panel.show(findings(high, info));

        table().setRowSelectionInterval(1, 1);

        assertThat(selected).containsExactly(info);
    }

    @Test
    void theSelectedIssueStaysSelectedWhenMoreIssuesArrive() {
        panel.show(findings(info));
        table().setRowSelectionInterval(0, 0);
        selected.clear();

        panel.show(findings(info, high));

        assertThat(table().getSelectedRow()).isEqualTo(1);
        assertThat(selected).isEmpty();
    }

    @Test
    void theTestedColumnNamesTheOperationTheScanTestedEvenWhenThePayloadReplacedItsName() {
        MontoyaTestFactory.install();
        String payload = "http://w52z.oastify.com";
        HttpRequest call = HttpRequest.httpRequestFromUrl("https://mcp.example/mcp").withMethod("POST")
                .withBody("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\""
                        + payload + "\",\"arguments\":{}}}");
        int start = new String(call.toByteArray().getBytes(), StandardCharsets.ISO_8859_1).indexOf(payload);
        AuditIssue outbound = Issues.issue("Outbound request", AuditIssueSeverity.HIGH, "<p>d</p>",
                List.of(HttpRequestResponse.httpRequestResponse(call, null)
                        .withRequestMarkers(List.of(Marker.marker(start, start + payload.length())))));

        panel.show(new ScanFindings(List.of(outbound), 1, Map.of(), Map.of(outbound, "tools/call ping_host")));

        assertThat(table().getValueAt(0, IssueTableModel.Column.TESTED.ordinal()))
                .isEqualTo("tools/call ping_host · name");
    }

    private static ScanFindings findings(AuditIssue... issues) {
        return new ScanFindings(List.of(issues), issues.length, Map.of());
    }

    private JLabel renderedSeverity(int row) {
        JTable table = table();
        return (JLabel) table.prepareRenderer(table.getCellRenderer(row, 0), row, 0);
    }

    private static AuditIssue rated(String name, AuditIssueSeverity severity, AuditIssueConfidence confidence) {
        AuditIssue issue = Issues.issue(name, severity);
        when(issue.confidence()).thenReturn(confidence);
        return issue;
    }

    private JTable table() {
        return Find.named(panel, JTable.class, IssueListPanel.TABLE);
    }
}
