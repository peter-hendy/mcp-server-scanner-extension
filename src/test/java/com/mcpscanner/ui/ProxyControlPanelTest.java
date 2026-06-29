package com.mcpscanner.ui;

import com.mcpscanner.proxy.observe.preflight.LabelledResult;
import com.mcpscanner.proxy.observe.preflight.PreflightReport;
import com.mcpscanner.proxy.observe.preflight.PreflightResult;
import org.junit.jupiter.api.Test;

import java.awt.event.ActionEvent;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ProxyControlPanelTest {

    @Test
    void renderEmptyReportSaysNoChecks() {
        assertThat(ProxyControlPanel.render(new PreflightReport(List.of())))
                .isEqualTo("No preflight checks.");
    }

    @Test
    void renderShowsStatusBadgeLabelAndMessagePerCheck() {
        PreflightReport report = new PreflightReport(List.of(
                new LabelledResult("Burp version", PreflightResult.ok("2025.8 meets the minimum")),
                new LabelledResult("Streaming settings", PreflightResult.warn("disable streaming in Proxy options"))));

        String rendered = ProxyControlPanel.render(report);

        // Badges are padded to a fixed 6-char width so the label column lines up; the shorter [OK]
        // arm must carry its padding (a regression to a bare "[OK]" would lose the trailing spaces).
        assertThat(rendered).contains("[OK]    Burp version — 2025.8 meets the minimum");
        assertThat(rendered).contains("[WARN]  Streaming settings — disable streaming in Proxy options");
    }

    @Test
    void renderAppendsFailureNoticeWhenAnyCheckFails() {
        PreflightReport report = new PreflightReport(List.of(
                new LabelledResult("Listener", PreflightResult.fail("port 8080 not reachable"))));

        String rendered = ProxyControlPanel.render(report);

        assertThat(rendered).contains("[FAIL]");
        assertThat(rendered).contains("FAILED");
    }

    @Test
    void renderHasNoFailureNoticeWhenAllPass() {
        PreflightReport report = new PreflightReport(List.of(
                new LabelledResult("Burp version", PreflightResult.ok("fine")),
                new LabelledResult("Streaming", PreflightResult.warn("verify manually"))));

        assertThat(ProxyControlPanel.render(report)).doesNotContain("FAILED");
    }

    @Test
    void toggleStartsAtInitialStateAndReportsChanges() {
        AtomicReference<Boolean> toggled = new AtomicReference<>();
        ProxyControlPanel panel = new ProxyControlPanel(true, toggled::set);

        assertThat(panel.enableToggleForTest().isSelected()).isTrue();

        panel.enableToggleForTest().setSelected(false);
        fireAction(panel);

        assertThat(toggled.get()).isFalse();
    }

    @Test
    void preflightButtonDisabledUntilSourceAttached() {
        ProxyControlPanel panel = new ProxyControlPanel(false, ignored -> {});

        assertThat(panel.runPreflightButtonForTest().isEnabled()).isFalse();

        panel.attachPreflightSource(() -> new PreflightReport(List.of(
                new LabelledResult("Burp version", PreflightResult.ok("fine")))));

        assertThat(panel.runPreflightButtonForTest().isEnabled()).isTrue();
    }

    @Test
    void runningPreflightRendersTheReportIntoTheArea() {
        ProxyControlPanel panel = new ProxyControlPanel(false, ignored -> {});
        panel.attachPreflightSource(() -> new PreflightReport(List.of(
                new LabelledResult("Listener", PreflightResult.ok("port 8080 reachable")))));

        panel.runPreflightButtonForTest().doClick();

        assertThat(panel.preflightAreaForTest().getText()).contains("port 8080 reachable");
    }

    private static void fireAction(ProxyControlPanel panel) {
        for (var listener : panel.enableToggleForTest().getActionListeners()) {
            listener.actionPerformed(new ActionEvent(panel.enableToggleForTest(), ActionEvent.ACTION_PERFORMED, "x"));
        }
    }
}
