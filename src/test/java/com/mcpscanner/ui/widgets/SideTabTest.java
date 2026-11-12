package com.mcpscanner.ui.widgets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.UIManager;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.FontUIResource;
import javax.swing.plaf.InsetsUIResource;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SideTabTest {

    private static final Color UNDERLINE = new Color(0xE8612C);
    private static final Color HOVER = new Color(0x123456);
    private static final Color BACKGROUND = new Color(0xFAFAFA);
    private static final List<String> KEYS = List.of("TabbedPane.font", "TabbedPane.underlineColor",
            "TabbedPane.hoverColor", "TabbedPane.tabInsets", "TabbedPane.tabHeight", "TabbedPane.tabSelectionHeight",
            "Panel.background", "TabbedPane.hoverForeground");

    private final Map<String, Object> saved = new HashMap<>();

    @BeforeEach
    void installTabTheme() {
        KEYS.forEach(key -> saved.put(key, UIManager.get(key)));
        UIManager.put("TabbedPane.font", new FontUIResource("Dialog", Font.PLAIN, 17));
        UIManager.put("TabbedPane.underlineColor", new ColorUIResource(UNDERLINE));
        UIManager.put("TabbedPane.hoverColor", new ColorUIResource(HOVER));
        UIManager.put("TabbedPane.tabInsets", new InsetsUIResource(4, 14, 4, 14));
        UIManager.put("TabbedPane.tabHeight", 36);
        UIManager.put("TabbedPane.tabSelectionHeight", 3);
        UIManager.put("Panel.background", new ColorUIResource(BACKGROUND));
    }

    @AfterEach
    void restoreTheme() {
        saved.forEach(UIManager::put);
    }

    @Test
    void usesTheLookAndFeelTabFont() {
        SideTab tab = new SideTab("Authentication");

        assertThat(tab.getFont().getSize()).isEqualTo(17);
        assertThat(tab.getFont().isBold()).isFalse();
    }

    @Test
    void isAsWideAsATabIsTallAndPaddedLikeATab() {
        SideTab tab = new SideTab("Checks");
        FontMetrics metrics = tab.getFontMetrics(tab.getFont());

        Dimension size = tab.getPreferredSize();

        assertThat(size.width).isEqualTo(36);
        assertThat(size.height).isEqualTo(metrics.stringWidth("Checks") + 28);
    }

    @Test
    void growsWithTheFontLikeABurpTabWhoseInsetsPadTheText() {
        UIManager.put("TabbedPane.tabInsets", new InsetsUIResource(9, 15, 9, 15));
        UIManager.put("TabbedPane.font", new FontUIResource("Dialog", Font.PLAIN, 30));
        SideTab tab = new SideTab("Checks");

        assertThat(tab.getPreferredSize().width)
                .isEqualTo(tab.getFontMetrics(tab.getFont()).getHeight() + 18);
    }

    @Test
    void hoveringTheSelectedTabKeepsItLookingSelected() {
        SideTab tab = new SideTab("Log");
        tab.setSelected(true);
        tab.getModel().setRollover(true);

        BufferedImage painted = paint(tab);

        assertThat(new Color(painted.getRGB(0, painted.getHeight() / 2))).isEqualTo(UNDERLINE);
    }

    @Test
    void whenTheThemeHasAHoverForegroundHoverChangesOnlyTheText() {
        UIManager.put("TabbedPane.hoverForeground", new ColorUIResource(Color.MAGENTA));
        SideTab tab = new SideTab("Log");
        tab.getModel().setRollover(true);

        BufferedImage painted = paint(tab);

        assertThat(new Color(painted.getRGB(painted.getWidth() - 2, 1))).isEqualTo(BACKGROUND);
    }

    @Test
    void hoveringUsesTheTabHoverForeground() {
        UIManager.put("TabbedPane.hoverForeground", new ColorUIResource(Color.MAGENTA));
        SideTab tab = new SideTab("Log");
        tab.getModel().setRollover(true);

        assertThat(tab.labelColor()).isEqualTo(Color.MAGENTA);
    }

    @Test
    void theSelectedTabCarriesTheAccentBarOnTheEdgeFacingTheContent() {
        SideTab tab = new SideTab("Log");
        tab.setSelected(true);

        BufferedImage painted = paint(tab);

        assertThat(new Color(painted.getRGB(0, painted.getHeight() / 2))).isEqualTo(UNDERLINE);
        assertThat(new Color(painted.getRGB(2, painted.getHeight() / 2))).isEqualTo(UNDERLINE);
        assertThat(new Color(painted.getRGB(3, 1))).isNotEqualTo(UNDERLINE);
        assertThat(new Color(painted.getRGB(painted.getWidth() - 1, painted.getHeight() / 2))).isNotEqualTo(UNDERLINE);
    }

    @Test
    void anUnselectedTabHasNoAccentBar() {
        BufferedImage painted = paint(new SideTab("Log"));

        assertThat(new Color(painted.getRGB(0, painted.getHeight() / 2))).isNotEqualTo(UNDERLINE);
    }

    @Test
    void hoveringATabShowsAMutedAccentBarWeakerThanTheSelection() {
        SideTab tab = new SideTab("Log");
        tab.getModel().setRollover(true);

        BufferedImage painted = paint(tab);

        assertThat(new Color(painted.getRGB(0, painted.getHeight() / 2))).isEqualTo(ThemeColors.muted());
        assertThat(new Color(painted.getRGB(0, 1))).isEqualTo(BACKGROUND);
        assertThat(new Color(painted.getRGB(painted.getWidth() - 2, 1))).isEqualTo(BACKGROUND);
    }

    @Test
    void anAlertColoursTheLabelAndMarksIt() {
        SideTab tab = new SideTab("Log");

        tab.showAlert("New errors were logged");

        assertThat(tab.text()).isEqualTo("Log ●");
        assertThat(tab.getToolTipText()).isEqualTo("New errors were logged");
        assertThat(tab.getForeground()).isEqualTo(ThemeColors.error());

        tab.showPlain();

        assertThat(tab.text()).isEqualTo("Log");
        assertThat(tab.getToolTipText()).isEqualTo("Show or hide Log");
    }

    @Test
    void followsAThemeChange() {
        SideTab tab = new SideTab("Log");
        UIManager.put("TabbedPane.font", new FontUIResource("Dialog", Font.PLAIN, 21));

        tab.updateUI();

        assertThat(tab.getFont().getSize()).isEqualTo(21);
    }

    private static BufferedImage paint(SideTab tab) {
        tab.setSize(tab.getPreferredSize());
        BufferedImage image = new BufferedImage(tab.getWidth(), tab.getHeight(), BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        graphics.setColor(BACKGROUND);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        tab.paint(graphics);
        graphics.dispose();
        return image;
    }
}
