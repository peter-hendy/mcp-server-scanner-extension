package com.mcpscanner.ui.sendto;

import org.junit.jupiter.api.Test;

import javax.swing.KeyStroke;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import static org.assertj.core.api.Assertions.assertThat;

class SendToShortcutsTest {

    private static final int SHORTCUT = BurpHotkey.shortcutMask();

    @Test
    void withoutBurpSettingsTheBurpDefaultsApply() {
        SendToShortcuts shortcuts = SendToShortcuts.defaults();

        assertThat(shortcuts.keyStrokeFor("Repeater")).contains(key(KeyEvent.VK_R, 0));
        assertThat(shortcuts.keyStrokeFor("Intruder")).contains(key(KeyEvent.VK_I, 0));
        assertThat(shortcuts.keyStrokeFor("Organizer")).contains(key(KeyEvent.VK_O, 0));
        assertThat(shortcuts.keyStrokeFor("Comparer")).isEmpty();
        assertThat(shortcuts.keyStrokeFor("Decoder")).isEmpty();
    }

    @Test
    void theUsersConfiguredHotkeysOverrideTheDefaults() {
        SendToShortcuts shortcuts = new SendToShortcuts(() -> """
                {"user_options":{"misc":{"hotkeys":[
                  {"action":"send_to_repeater","hotkey":"Ctrl+Shift+Y"},
                  {"action":"send_to_comparer","hotkey":"Ctrl+M"},
                  {"action":"send_to_intruder","hotkey":""}
                ]}}}""");

        assertThat(shortcuts.keyStrokeFor("Repeater")).contains(key(KeyEvent.VK_Y, InputEvent.SHIFT_DOWN_MASK));
        assertThat(shortcuts.keyStrokeFor("Comparer")).contains(key(KeyEvent.VK_M, 0));
        assertThat(shortcuts.keyStrokeFor("Intruder")).isEmpty();
        assertThat(shortcuts.keyStrokeFor("Organizer")).contains(key(KeyEvent.VK_O, 0));
    }

    @Test
    void unreadableBurpSettingsFallBackToTheDefaults() {
        assertThat(new SendToShortcuts(() -> "not json").keyStrokeFor("Repeater")).contains(key(KeyEvent.VK_R, 0));
        assertThat(new SendToShortcuts(() -> {
            throw new IllegalStateException("no Burp");
        }).keyStrokeFor("Repeater")).contains(key(KeyEvent.VK_R, 0));
    }

    private static KeyStroke key(int code, int extraModifiers) {
        return KeyStroke.getKeyStroke(code, SHORTCUT | extraModifiers);
    }
}
