package com.mcpscanner.ui.sendto;

import javax.swing.KeyStroke;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class BurpHotkey {

    private static final String SEPARATOR = "+";
    private static final Map<String, Integer> MODIFIERS = Map.of(
            "Ctrl", shortcutMask(),
            "Shift", InputEvent.SHIFT_DOWN_MASK,
            "Alt", InputEvent.ALT_DOWN_MASK);

    private BurpHotkey() {
    }

    public static Optional<KeyStroke> parse(String hotkey) {
        if (hotkey == null || hotkey.isBlank() || hotkey.endsWith(SEPARATOR)) {
            return Optional.empty();
        }
        int keyStart = hotkey.lastIndexOf(SEPARATOR) + 1;
        return keyCode(hotkey.substring(keyStart))
                .map(code -> KeyStroke.getKeyStroke(code, modifiers(hotkey.substring(0, keyStart))));
    }

    static int shortcutMask() {
        return GraphicsEnvironment.isHeadless()
                ? InputEvent.CTRL_DOWN_MASK
                : Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
    }

    private static int modifiers(String modifierText) {
        int mask = 0;
        for (String name : modifierText.split("\\" + SEPARATOR)) {
            mask |= MODIFIERS.getOrDefault(name, 0);
        }
        return mask;
    }

    private static Optional<Integer> keyCode(String keyName) {
        KeyStroke named = KeyStroke.getKeyStroke(keyName.toUpperCase(Locale.ROOT));
        return Optional.ofNullable(named).map(KeyStroke::getKeyCode);
    }
}
