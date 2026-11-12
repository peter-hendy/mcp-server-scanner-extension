package com.mcpscanner.ui.serverinfo;

import com.fasterxml.jackson.databind.JsonNode;
import com.mcpscanner.ui.detail.Chip;
import com.mcpscanner.ui.detail.DetailLabels;
import com.mcpscanner.ui.detail.IconStrip;
import com.mcpscanner.ui.widgets.Placeholders;
import com.mcpscanner.ui.widgets.UiStyle;
import com.mcpscanner.ui.widgets.WrappingText;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Component;
import java.awt.Font;
import java.util.Map;
import java.util.stream.Stream;

public final class ServerIdentityView extends JPanel {

    public static final String NO_IDENTITY = "The server did not advertise its name or version.";

    private static final int HEADING_GAP = 8;
    private static final float HEADING_SIZE_INCREASE = 4f;
    private static final String VERSION_PREFIX = "v";

    private final Map<String, String> serverInfo;
    private final JLabel heading;
    private final JLabel monospacedName;
    private final JComponent website;

    private ServerIdentityView(Map<String, String> serverInfo, IconStrip icons) {
        this.serverInfo = serverInfo;
        this.heading = headingLabel(headingText());
        this.monospacedName = nameBesideTitle();
        this.website = websiteText();
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setOpaque(false);
        setAlignmentX(Component.LEFT_ALIGNMENT);
        if (hasIdentity() || icons.isVisible()) {
            addParts(icons);
        } else {
            add(DetailLabels.leftAligned(Placeholders.wrapping(NO_IDENTITY)));
        }
    }

    public static ServerIdentityView of(Map<String, String> serverInfo, JsonNode icons) {
        return new ServerIdentityView(serverInfo == null ? Map.of() : serverInfo, IconStrip.of(icons));
    }

    public JLabel heading() {
        return heading;
    }

    public JLabel monospacedName() {
        return monospacedName;
    }

    public JComponent website() {
        return website;
    }

    private void addParts(IconStrip icons) {
        add(headingRow());
        add(shownWhenPresent(new WrappingText(field("description"))));
        add(website);
        add(icons);
    }

    private JPanel headingRow() {
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.setOpaque(false);
        row.add(heading);
        addSpaced(row, monospacedName);
        if (!field("version").isBlank()) {
            addSpaced(row, Chip.neutral(VERSION_PREFIX + field("version")));
        }
        return DetailLabels.leftAligned(row);
    }

    private static void addSpaced(JPanel row, JComponent part) {
        Component gap = Box.createHorizontalStrut(HEADING_GAP);
        gap.setVisible(part.isVisible());
        row.add(gap);
        row.add(part);
    }

    private JLabel nameBesideTitle() {
        JLabel name = UiStyle.monospaced(DetailLabels.muted(field("name")));
        name.setVisible(!field("title").isBlank() && !field("name").isBlank());
        return name;
    }

    private JComponent websiteText() {
        return shownWhenPresent(UiStyle.monospaced(new WrappingText(field("websiteUrl")).muted()));
    }

    private String headingText() {
        return field("title").isBlank() ? field("name") : field("title");
    }

    private boolean hasIdentity() {
        return Stream.of("name", "title", "version", "description", "websiteUrl")
                .anyMatch(key -> !field(key).isBlank());
    }

    private String field(String key) {
        String value = serverInfo.get(key);
        return value == null ? "" : value;
    }

    private static JLabel headingLabel(String text) {
        JLabel label = DetailLabels.literal(text);
        Font font = label.getFont();
        if (font != null) {
            label.setFont(font.deriveFont(Font.BOLD, font.getSize2D() + HEADING_SIZE_INCREASE));
        }
        return label;
    }

    private static JComponent shownWhenPresent(WrappingText text) {
        text.setVisible(!text.getText().isBlank());
        return DetailLabels.leftAligned(text);
    }
}
