package com.mcpscanner.ui.sendto;

import org.junit.jupiter.api.Test;

import javax.swing.KeyStroke;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import static org.assertj.core.api.Assertions.assertThat;

class BurpHotkeyTest {

    private static final int SHORTCUT = BurpHotkey.shortcutMask();

    @Test
    void ctrlMeansThePlatformMenuShortcutKey() {
        assertThat(BurpHotkey.parse("Ctrl+R")).contains(KeyStroke.getKeyStroke(KeyEvent.VK_R, SHORTCUT));
    }

    @Test
    void shiftAndAltCombineWithTheShortcutKey() {
        assertThat(BurpHotkey.parse("Ctrl+Shift+Alt+I")).contains(KeyStroke.getKeyStroke(KeyEvent.VK_I,
                SHORTCUT | InputEvent.SHIFT_DOWN_MASK | InputEvent.ALT_DOWN_MASK));
    }

    @Test
    void namedKeysUseTheirKeyCodes() {
        assertThat(BurpHotkey.parse("Ctrl+Space")).contains(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, SHORTCUT));
        assertThat(BurpHotkey.parse("Ctrl+Equals")).contains(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, SHORTCUT));
        assertThat(BurpHotkey.parse("Ctrl+F5")).contains(KeyStroke.getKeyStroke(KeyEvent.VK_F5, SHORTCUT));
    }

    @Test
    void anEmptyOrUnreadableHotkeyMeansNoBinding() {
        assertThat(BurpHotkey.parse("")).isEmpty();
        assertThat(BurpHotkey.parse("Ctrl+")).isEmpty();
        assertThat(BurpHotkey.parse("Ctrl+NotAKey")).isEmpty();
        assertThat(BurpHotkey.parse(null)).isEmpty();
    }
}
