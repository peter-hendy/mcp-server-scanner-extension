package com.mcpscanner.ui;

import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.scan.McpScanLauncher;
import com.mcpscanner.scan.ScanMode;
import com.mcpscanner.ui.widgets.WarningBanner;

import javax.swing.JButton;
import java.util.Objects;
import java.util.function.Supplier;

final class PausedTasksNotice {

    static final String MESSAGE = "Burp task execution is paused, so Burp Scanner audits will not run. "
            + "Resume it here or from Burp's Dashboard.";
    static final String RESUME = "Resume Burp tasks";
    static final String RESUMED = "Burp task execution resumed";

    private final Supplier<ScanMode> mode;
    private final McpScanLauncher launcher;
    private final McpEventLog eventLog;
    private final WarningBanner banner = new WarningBanner(MESSAGE, new JButton(RESUME));

    PausedTasksNotice(Supplier<ScanMode> mode, McpScanLauncher launcher, McpEventLog eventLog) {
        this.mode = Objects.requireNonNull(mode);
        this.launcher = Objects.requireNonNull(launcher);
        this.eventLog = Objects.requireNonNull(eventLog);
        banner.setVisible(false);
        banner.actionButton().addActionListener(event -> resume());
    }

    WarningBanner banner() {
        return banner;
    }

    void refresh() {
        boolean paused = launcher.waitsForPausedTasks(mode.get());
        if (paused && !banner.isVisible()) {
            eventLog.warn(MESSAGE);
        }
        banner.setVisible(paused);
    }

    private void resume() {
        launcher.resumePausedTasks(mode.get());
        eventLog.info(RESUMED);
        refresh();
    }
}
