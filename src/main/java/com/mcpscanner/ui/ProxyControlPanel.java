package com.mcpscanner.ui;

import com.mcpscanner.proxy.observe.preflight.LabelledResult;
import com.mcpscanner.proxy.observe.preflight.PreflightReport;
import com.mcpscanner.proxy.observe.preflight.PreflightStatus;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Font;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Header control for the Traffic tab: a checkbox toggling the {@code mcpProxyEnabled} setting and a
 * button that runs the {@link com.mcpscanner.proxy.observe.preflight.ProxyPreflight} and renders the
 * OK/WARN/FAIL list so the operator sees what to verify manually.
 *
 * <p>The toggle's persistence and the preflight source are injected as plain functional types so this
 * panel stays free of config/proxy runtime dependencies and the formatting stays unit-testable. All
 * methods are Swing-EDT only, like every other panel.
 */
public final class ProxyControlPanel extends JPanel {

    private final JCheckBox enableToggle;
    private final JTextArea preflightArea;
    private final JButton runPreflightButton;
    private Supplier<PreflightReport> preflightSource;

    public ProxyControlPanel(boolean initiallyEnabled, Consumer<Boolean> onToggle) {
        super(new BorderLayout());
        this.enableToggle = new JCheckBox("Enable MCP Proxy (live observation)", initiallyEnabled);
        this.enableToggle.setToolTipText(
                "When on, scanner-family traffic is swapped through the local proxy and live MCP "
                        + "responses are passively scanned and shown below. When off, behaviour is "
                        + "unchanged from before this feature.");
        this.preflightArea = buildPreflightArea();
        this.runPreflightButton = new JButton("Run preflight");
        this.runPreflightButton.setEnabled(false);

        enableToggle.addActionListener(e -> onToggle.accept(enableToggle.isSelected()));
        runPreflightButton.addActionListener(e -> refreshPreflight());

        setBorder(new EmptyBorder(8, 8, 4, 8));
        add(buildControlRow(), BorderLayout.NORTH);
        add(buildPreflightPanel(), BorderLayout.CENTER);
    }

    /**
     * Supplies the preflight report and enables the button. Called once by the composition root after
     * the module is built; until then the button stays disabled (no source to run).
     */
    public void attachPreflightSource(Supplier<PreflightReport> source) {
        this.preflightSource = source;
        runPreflightButton.setEnabled(source != null);
    }

    private void refreshPreflight() {
        if (preflightSource == null) {
            return;
        }
        preflightArea.setText(render(preflightSource.get()));
        preflightArea.setCaretPosition(0);
    }

    /**
     * Pure formatter: one {@code STATUS  label — message} line per check. Package-visible so it is
     * unit-testable without standing up Swing.
     */
    static String render(PreflightReport report) {
        List<LabelledResult> results = report.results();
        if (results.isEmpty()) {
            return "No preflight checks.";
        }
        String body = results.stream()
                .map(ProxyControlPanel::renderLine)
                .collect(Collectors.joining("\n"));
        if (report.hasFailures()) {
            return body + "\n\nOne or more checks FAILED — the proxy may not observe traffic until fixed.";
        }
        return body;
    }

    private static String renderLine(LabelledResult result) {
        return badge(result.status()) + "  " + result.label() + " — " + result.result().message();
    }

    /** Left-pads every status badge to this width so the {@code label — message} column lines up. */
    private static final int BADGE_WIDTH = 6;

    private static String badge(PreflightStatus status) {
        return String.format("%-" + BADGE_WIDTH + "s", "[" + status + "]");
    }

    private JPanel buildControlRow() {
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.LINE_AXIS));
        row.add(enableToggle);
        row.add(Box.createHorizontalStrut(16));
        row.add(runPreflightButton);
        row.add(Box.createHorizontalGlue());
        return row;
    }

    private JPanel buildPreflightPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createCompoundBorder(
                new EmptyBorder(8, 0, 0, 0),
                BorderFactory.createTitledBorder("Proxy preflight")));
        JLabel hint = new JLabel("Run preflight to verify the proxy setup. WARN items are manual reminders.");
        hint.setBorder(new EmptyBorder(2, 4, 4, 4));
        panel.add(hint, BorderLayout.NORTH);
        panel.add(preflightArea, BorderLayout.CENTER);
        return panel;
    }

    private static JTextArea buildPreflightArea() {
        JTextArea area = new JTextArea();
        area.setEditable(false);
        area.setOpaque(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setRows(6);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, area.getFont().getSize()));
        area.setBorder(new EmptyBorder(4, 4, 4, 4));
        return area;
    }

    JCheckBox enableToggleForTest() {
        return enableToggle;
    }

    JButton runPreflightButtonForTest() {
        return runPreflightButton;
    }

    JTextArea preflightAreaForTest() {
        return preflightArea;
    }
}
