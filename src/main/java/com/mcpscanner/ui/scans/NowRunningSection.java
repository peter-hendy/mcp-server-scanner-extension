package com.mcpscanner.ui.scans;

import com.mcpscanner.engine.RunningInvocation;
import com.mcpscanner.ui.detail.DetailLabels;

import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.util.List;

final class NowRunningSection extends JPanel {

    static final String NAME = "scan-now-running";
    static final String LINES = "scan-now-running-lines";

    private final ScanText text;
    private final JPanel lines = Named.as(LINES, new JPanel());
    private List<RunningInvocation> shown = List.of();

    NowRunningSection(ScanText text) {
        super(new BorderLayout());
        this.text = text;
        setName(NAME);
        lines.setLayout(new BoxLayout(lines, BoxLayout.Y_AXIS));
        add(DetailLabels.bold(new JLabel("Now running")), BorderLayout.NORTH);
        add(lines, BorderLayout.CENTER);
        setVisible(false);
    }

    void show(List<RunningInvocation> running) {
        if (running.equals(shown)) {
            return;
        }
        shown = running;
        lines.removeAll();
        running.forEach(invocation -> lines.add(DetailLabels.literal(text.nowRunning(invocation))));
        setVisible(!running.isEmpty());
        revalidate();
    }
}
