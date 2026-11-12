package com.mcpscanner.ui.widgets;

import burp.api.montoya.scanner.audit.issues.AuditIssueSeverity;

import javax.swing.UIManager;
import java.awt.Color;
import java.util.function.Supplier;

public final class SeverityColors {

    private static final Color LOW_ON_LIGHT = new Color(0x9A7B00);
    private static final Color LOW_ON_DARK = new Color(0xE5C33A);
    private static final Color INFORMATION_ON_LIGHT = new Color(0x2563B8);
    private static final Color INFORMATION_ON_DARK = new Color(0x6AA8F0);

    private SeverityColors() {
    }

    public static Supplier<Color> of(AuditIssueSeverity severity) {
        if (severity == null) {
            return ThemeColors::muted;
        }
        return switch (severity) {
            case HIGH -> ThemeColors::error;
            case MEDIUM -> ThemeColors::warning;
            case LOW -> () -> onTheme(LOW_ON_LIGHT, LOW_ON_DARK);
            case INFORMATION -> () -> onTheme(INFORMATION_ON_LIGHT, INFORMATION_ON_DARK);
            case FALSE_POSITIVE -> ThemeColors::muted;
        };
    }

    private static Color onTheme(Color light, Color dark) {
        return ThemeColors.isDark(UIManager.getColor("Panel.background")) ? dark : light;
    }
}
