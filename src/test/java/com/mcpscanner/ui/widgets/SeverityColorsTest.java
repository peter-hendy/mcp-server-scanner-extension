package com.mcpscanner.ui.widgets;

import burp.api.montoya.scanner.audit.issues.AuditIssueSeverity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.swing.UIManager;
import java.awt.Color;

import static org.assertj.core.api.Assertions.assertThat;

class SeverityColorsTest {

    private final Color background = UIManager.getColor("Panel.background");

    @AfterEach
    void restoreTheme() {
        UIManager.put("Panel.background", background);
    }

    @Test
    void highAndMediumFollowTheThemesErrorAndWarningColours() {
        assertThat(SeverityColors.of(AuditIssueSeverity.HIGH).get()).isEqualTo(ThemeColors.error());
        assertThat(SeverityColors.of(AuditIssueSeverity.MEDIUM).get()).isEqualTo(ThemeColors.warning());
    }

    @Test
    void lowIsYellowAndInfoBlueInAShadeReadableOnTheTheme() {
        UIManager.put("Panel.background", Color.WHITE);
        Color lowOnLight = SeverityColors.of(AuditIssueSeverity.LOW).get();
        Color infoOnLight = SeverityColors.of(AuditIssueSeverity.INFORMATION).get();
        UIManager.put("Panel.background", new Color(0x2B2B2B));
        Color lowOnDark = SeverityColors.of(AuditIssueSeverity.LOW).get();
        Color infoOnDark = SeverityColors.of(AuditIssueSeverity.INFORMATION).get();

        assertThat(lowOnLight).isNotEqualTo(lowOnDark);
        assertThat(infoOnLight).isNotEqualTo(infoOnDark);
        assertThat(yellow(lowOnLight)).isTrue();
        assertThat(yellow(lowOnDark)).isTrue();
        assertThat(blue(infoOnLight)).isTrue();
        assertThat(blue(infoOnDark)).isTrue();
        assertThat(ThemeColors.isDark(lowOnLight)).isNotEqualTo(ThemeColors.isDark(lowOnDark));
    }

    @Test
    void aFalsePositiveOrUnratedIssueIsMuted() {
        assertThat(SeverityColors.of(AuditIssueSeverity.FALSE_POSITIVE).get()).isEqualTo(ThemeColors.muted());
        assertThat(SeverityColors.of(null).get()).isEqualTo(ThemeColors.muted());
    }

    private static boolean yellow(Color color) {
        return color.getRed() > color.getBlue() + 60 && color.getGreen() > color.getBlue() + 40;
    }

    private static boolean blue(Color color) {
        return color.getBlue() > color.getRed() + 60;
    }
}
