package com.mcpscanner.ui;

import com.mcpscanner.config.ExtensionConfigStore;
import com.mcpscanner.scan.ScanMode;
import com.mcpscanner.scan.ScanModeSetting;
import com.mcpscanner.testutil.InMemoryPersistedObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import javax.swing.JLabel;
import javax.swing.JRadioButtonMenuItem;
import java.util.Arrays;
import java.util.List;

import static com.mcpscanner.ui.McpScannerTabFixture.onEdt;
import static org.assertj.core.api.Assertions.assertThat;

class ScanModeSelectorTest {

    private static final String DROPDOWN_ARROW = " \u25BE";

    private final ExtensionConfigStore store = InMemoryPersistedObject.configStore();
    private final ScanModeSetting setting = new ScanModeSetting(store);

    @Test
    void menuOffersEveryModeWithTheCurrentOneSelected() throws Exception {
        onEdt(() -> {
            ScanModeSelector selector = new ScanModeSelector(setting);

            assertThat(items(selector)).extracting(JRadioButtonMenuItem::getText).containsExactlyElementsOf(
                    Arrays.stream(ScanMode.values()).map(mode -> ScanModeText.of(mode).menuLabel()).toList());
            assertThat(selectedLabel(selector)).isEqualTo(label(setting.current()));
        });
    }

    @Test
    void menuDescribesWhatEachModeRuns() throws Exception {
        onEdt(() -> assertThat(Arrays.stream(new ScanModeSelector(setting).menu().getComponents())
                .filter(JLabel.class::isInstance).map(JLabel.class::cast).map(JLabel::getText))
                .containsExactly("MCP checks only, run by the extension",
                        "MCP checks plus Burp's built-in active checks, as a Burp audit task"));
    }

    @Test
    void buttonTooltipSaysWhatTheCurrentModeRuns() throws Exception {
        setting.select(ScanMode.MCP_ONLY);
        onEdt(() -> assertThat(new ScanModeSelector(setting).button().getToolTipText())
                .isEqualTo("Scan mode: Scan with extension (recommended): MCP checks only, run by the extension. "
                        + "Click to change the scan mode."));
    }

    @Test
    void buttonEndsWithADropdownArrow() throws Exception {
        onEdt(() -> assertThat(new ScanModeSelector(setting).button().getText()).endsWith(DROPDOWN_ARROW));
    }

    @Test
    void buttonNamesWhichChecksScanRuns() throws Exception {
        setting.select(ScanMode.MCP_ONLY);
        onEdt(() -> assertThat(new ScanModeSelector(setting).button().getText())
                .isEqualTo("Mode: Extension" + DROPDOWN_ARROW));

        setting.select(ScanMode.MCP_AND_BURP);
        onEdt(() -> assertThat(new ScanModeSelector(setting).button().getText())
                .isEqualTo("Mode: Burp Scanner" + DROPDOWN_ARROW));
    }

    @ParameterizedTest
    @EnumSource(ScanMode.class)
    void aReloadedSettingShowsTheSavedModeInTheButtonAndMenu(ScanMode mode) throws Exception {
        setting.select(mode);

        onEdt(() -> {
            ScanModeSelector reloaded = new ScanModeSelector(new ScanModeSetting(store));

            assertShows(reloaded, mode);
        });
    }

    @ParameterizedTest
    @EnumSource(ScanMode.class)
    void choosingAModePersistsIt(ScanMode mode) throws Exception {
        onEdt(() -> {
            ScanModeSelector selector = new ScanModeSelector(setting);

            itemFor(selector, mode).doClick();
        });

        assertThat(setting.current()).isEqualTo(mode);
    }

    @ParameterizedTest
    @EnumSource(ScanMode.class)
    void refreshingShowsAModeChosenElsewhere(ScanMode mode) throws Exception {
        onEdt(() -> {
            ScanModeSelector selector = new ScanModeSelector(setting);
            setting.select(mode);

            selector.refresh();

            assertShows(selector, mode);
        });
    }

    private static void assertShows(ScanModeSelector selector, ScanMode mode) {
        assertThat(selectedLabel(selector)).isEqualTo(label(mode));
        assertThat(selector.button().getText()).isEqualTo("Mode: " + ScanModeText.of(mode).shortLabel() + DROPDOWN_ARROW);
        assertThat(selector.button().getToolTipText()).contains(ScanModeText.of(mode).description());
    }

    private static String label(ScanMode mode) {
        return ScanModeText.of(mode).menuLabel();
    }

    private static JRadioButtonMenuItem itemFor(ScanModeSelector selector, ScanMode mode) {
        return items(selector).stream().filter(item -> item.getText().equals(label(mode))).findFirst().orElseThrow();
    }

    private static List<JRadioButtonMenuItem> items(ScanModeSelector selector) {
        return Arrays.stream(selector.menu().getComponents())
                .filter(JRadioButtonMenuItem.class::isInstance)
                .map(JRadioButtonMenuItem.class::cast)
                .toList();
    }

    private static String selectedLabel(ScanModeSelector selector) {
        return items(selector).stream().filter(JRadioButtonMenuItem::isSelected)
                .map(JRadioButtonMenuItem::getText).findFirst().orElseThrow();
    }
}
