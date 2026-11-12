package com.mcpscanner.ui;

import com.mcpscanner.scan.ScanActivity;
import com.mcpscanner.scan.ScanMode;
import com.mcpscanner.ui.widgets.OffEdtFeed;
import com.mcpscanner.ui.widgets.Plurals;
import com.mcpscanner.ui.widgets.UiReads;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

final class ScanActivityPanel extends JPanel {

    private static final int REFRESH_MS = 1000;
    private static final String SEPARATOR = " · ";
    private static final String SENTENCE_BREAK = ". ";
    private static final String MCP_ONLY_HINT =
            "Extension scans: results in " + ScanModeText.of(ScanMode.MCP_ONLY).resultsLocation();
    private static final String BURP_AUDIT_HINT =
            "Burp audits: results in " + ScanModeText.of(ScanMode.MCP_AND_BURP).resultsLocation();
    private static final String STOP_MCP_ONLY = "Stop the running extension scans";
    private static final String STOP_BURP_AUDITS = "Stop and delete the Burp audit tasks this extension started";

    private final OffEdtFeed<ScanActivity> activityFeed;
    private final JLabel summary = new JLabel();
    private final JButton stopButton = new JButton("Stop");
    private final Timer poller;

    ScanActivityPanel(Supplier<ScanActivity> source, Runnable stop, UiReads reads) {
        super(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        this.activityFeed = reads.feed(0, source, this::show);
        this.poller = new Timer(REFRESH_MS, event -> refresh());
        stopButton.addActionListener(event -> {
            reads.run(stop);
            refresh();
        });
        add(summary);
        add(stopButton);
        setVisible(false);
    }

    void refresh() {
        activityFeed.request();
    }

    private void show(ScanActivity activity) {
        setVisible(!activity.isIdle());
        summary.setText(describe(activity));
        summary.setToolTipText(String.join(SENTENCE_BREAK, byKind(activity, MCP_ONLY_HINT, BURP_AUDIT_HINT)));
        stopButton.setToolTipText(String.join(SENTENCE_BREAK, byKind(activity, STOP_MCP_ONLY, STOP_BURP_AUDITS)));
        if (activity.isIdle()) {
            poller.stop();
        } else if (!poller.isRunning()) {
            poller.start();
        }
    }

    void dispose() {
        activityFeed.cancel();
        poller.stop();
    }

    boolean isPolling() {
        return poller.isRunning();
    }

    String summaryText() {
        return summary.getText();
    }

    String summaryTooltip() {
        return summary.getToolTipText();
    }

    JButton stopButton() {
        return stopButton;
    }

    private static String describe(ScanActivity activity) {
        List<String> parts = byKind(activity,
                Plurals.count(activity.mcpOnlyScans(), "extension scan") + SEPARATOR
                        + Plurals.count(activity.probesRun(), "probe") + " run",
                Plurals.count(activity.burpAudits(), "Burp audit") + SEPARATOR
                        + Plurals.count(activity.auditRequests(), "request"));
        parts.add(Plurals.count(activity.issues(), "issue"));
        return String.join(SEPARATOR, parts);
    }

    private static List<String> byKind(ScanActivity activity, String mcpOnly, String burpAudit) {
        List<String> parts = new ArrayList<>();
        if (activity.mcpOnlyScans() > 0) {
            parts.add(mcpOnly);
        }
        if (activity.burpAudits() > 0) {
            parts.add(burpAudit);
        }
        return parts;
    }
}
