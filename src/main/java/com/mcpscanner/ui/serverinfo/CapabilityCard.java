package com.mcpscanner.ui.serverinfo;

import com.mcpscanner.ui.detail.DetailLabels;
import com.mcpscanner.ui.widgets.ThemeColors;
import com.mcpscanner.ui.widgets.UiStyle;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.Border;
import java.util.List;

public final class CapabilityCard extends JPanel {

    private static final String NOT_ADVERTISED = "Not advertised";
    private static final int PADDING_VERTICAL = 8;
    private static final int PADDING_HORIZONTAL = 10;
    private static final int DASH_LENGTH = 4;
    private static final int DASH_SPACING = 3;

    private final String title;
    private final List<CapabilityLine> lines;
    private final boolean advertised;

    private CapabilityCard(String title, List<CapabilityLine> lines, boolean advertised) {
        this.title = title;
        this.lines = List.copyOf(lines);
        this.advertised = advertised;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setOpaque(false);
        setBorder(BorderFactory.createCompoundBorder(outline(),
                BorderFactory.createEmptyBorder(PADDING_VERTICAL, PADDING_HORIZONTAL, PADDING_VERTICAL,
                        PADDING_HORIZONTAL)));
        add(titleLabel());
        this.lines.forEach(line -> add(lineLabel(line)));
    }

    static CapabilityCard advertised(String title, List<CapabilityLine> lines) {
        return new CapabilityCard(title, lines, true);
    }

    static CapabilityCard notAdvertised(String title) {
        return absent(title, NOT_ADVERTISED);
    }

    static CapabilityCard absent(String title, String note) {
        return new CapabilityCard(title, List.of(CapabilityLine.note(note)), false);
    }

    public String title() {
        return title;
    }

    public boolean isAdvertised() {
        return advertised;
    }

    public List<String> lineTexts() {
        return lines.stream().map(CapabilityLine::shownText).toList();
    }

    private Border outline() {
        return advertised
                ? BorderFactory.createLineBorder(ThemeColors.separator(), 1, true)
                : BorderFactory.createDashedBorder(ThemeColors.separator(), DASH_LENGTH, DASH_SPACING);
    }

    private JLabel titleLabel() {
        return DetailLabels.bold(advertised ? DetailLabels.literal(title) : DetailLabels.muted(title));
    }

    private static JLabel lineLabel(CapabilityLine line) {
        JLabel label = DetailLabels.literal(line.shownText());
        switch (line.mark()) {
            case ENABLED -> label.setForeground(ThemeColors.success());
            case DISABLED, NOTE -> label.setForeground(ThemeColors.muted());
            case VALUE -> UiStyle.monospaced(label);
        }
        return label;
    }
}
