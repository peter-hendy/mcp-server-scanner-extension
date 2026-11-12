package com.mcpscanner.ui.scans;

import com.mcpscanner.engine.ScanSnapshot;
import com.mcpscanner.engine.ScanStatus;
import com.mcpscanner.ui.detail.Chip;
import com.mcpscanner.ui.detail.ChipVariant;
import com.mcpscanner.ui.detail.DetailLabels;
import com.mcpscanner.ui.widgets.ThemeColors;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.Map;

final class ScanCard extends JPanel {

    static final String CARD_PREFIX = "scan-card-";
    static final String NAME = "scan-name";
    static final String SUMMARY = "scan-summary";
    static final String DETAILS = "scan-details";
    static final String EXPAND = "scan-expand";
    static final String PAUSE = "scan-pause";
    static final String CANCEL = "scan-cancel";
    static final String REMOVE = "scan-remove";
    private static final String OPEN = "▾";
    private static final String CLOSED = "▸";
    private static final Map<ScanStatus, ChipVariant> CHIP_VARIANTS = Map.of(
            ScanStatus.RUNNING, ChipVariant.NEUTRAL,
            ScanStatus.PAUSED, ChipVariant.ATTENTION,
            ScanStatus.FINISHED, ChipVariant.POSITIVE,
            ScanStatus.CANCELLED, ChipVariant.ATTENTION);

    private final long scanId;
    private final ScanText text;
    private final ScanControls controls;
    private final JButton arrow = Named.as(EXPAND, new JButton());
    private final JLabel name = Named.as(NAME, DetailLabels.bold(DetailLabels.literal("")));
    private final JPanel chipSlot = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    private final JLabel summary = Named.as(SUMMARY, DetailLabels.literal(""));
    private final JLabel details = Named.as(DETAILS, DetailLabels.muted(""));
    private final JButton pauseResume = Named.as(PAUSE, new JButton("Pause"));
    private final JButton cancel = Named.as(CANCEL, new JButton("Cancel"));
    private final JButton remove = Named.as(REMOVE, new JButton("Remove"));
    private final ScanCardBody body;
    private final PauseIntent pauseIntent = new PauseIntent();
    private Chip chip = Chip.neutral("");
    private ScanSnapshot current;
    private boolean expanded;

    ScanCard(ScanSnapshot first, ScanText text, CardServices services) {
        super(new BorderLayout());
        this.scanId = first.id();
        this.text = text;
        this.controls = services.controls();
        this.body = new ScanCardBody(text, services.navigator(), services.editors());
        this.expanded = !first.ended();
        setName(CARD_PREFIX + scanId);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, ThemeColors.separator()),
                BorderFactory.createEmptyBorder(6, 6, 6, 6)));
        arrow.addActionListener(event -> toggle());
        pauseResume.addActionListener(event -> pauseOrResume());
        cancel.addActionListener(event -> controls.cancel(scanId));
        remove.addActionListener(event -> controls.remove(scanId));
        add(header(), BorderLayout.NORTH);
        add(body, BorderLayout.CENTER);
        showExpansion();
        update(first);
    }

    void update(ScanSnapshot snapshot) {
        if (snapshot.equals(current)) {
            return;
        }
        current = snapshot;
        pauseIntent.settle(snapshot.status());
        name.setText(snapshot.label().name());
        showStatus(snapshot);
        summary.setText(text.summary(snapshot));
        details.setText(text.connection(snapshot.label()) + ScanText.SEPARATOR + text.times(snapshot));
        showPauseButton();
        boolean active = snapshot.status().active();
        pauseResume.setVisible(active);
        cancel.setVisible(active);
        remove.setVisible(snapshot.ended());
        body.update(snapshot);
    }

    private JPanel header() {
        JPanel title = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        arrow.setBorderPainted(false);
        arrow.setContentAreaFilled(false);
        title.add(arrow);
        title.add(name);
        title.add(chipSlot);
        title.add(summary);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        buttons.add(pauseResume);
        buttons.add(cancel);
        buttons.add(remove);
        JPanel top = new JPanel(new BorderLayout());
        top.add(title, BorderLayout.CENTER);
        top.add(buttons, BorderLayout.EAST);
        details.setBorder(BorderFactory.createEmptyBorder(2, 30, 0, 0));
        top.add(details, BorderLayout.SOUTH);
        return top;
    }

    private void showStatus(ScanSnapshot snapshot) {
        String status = text.status(snapshot);
        ChipVariant variant = CHIP_VARIANTS.get(snapshot.status());
        if (!status.equals(chip.getText()) || chip.variant() != variant) {
            chip = new Chip(status, variant);
            chipSlot.removeAll();
            chipSlot.add(chip);
            chipSlot.revalidate();
        }
    }

    private void toggle() {
        expanded = !expanded;
        showExpansion();
    }

    private void showExpansion() {
        arrow.setText(expanded ? OPEN : CLOSED);
        arrow.setToolTipText(expanded ? "Collapse" : "Expand");
        summary.setVisible(!expanded);
        body.setVisible(expanded);
        revalidate();
    }

    private void pauseOrResume() {
        if (pauseIntent.toggle(current.status())) {
            controls.pause(scanId);
        } else {
            controls.resume(scanId);
        }
        showPauseButton();
    }

    private void showPauseButton() {
        pauseResume.setText(pauseIntent.showsPaused(current.status()) ? "Resume" : "Pause");
    }
}
