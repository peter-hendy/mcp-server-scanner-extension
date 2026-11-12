package com.mcpscanner.ui.sendto;

import javax.swing.AbstractAction;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class SendToShortcutKeys {

    private static final String ACTION_PREFIX = "sendToShortcut:";

    private final JComponent scope;
    private final List<KeyStroke> installed = new ArrayList<>();

    SendToShortcutKeys(JComponent scope) {
        this.scope = Objects.requireNonNull(scope);
    }

    void replace(Map<KeyStroke, Runnable> actions) {
        removeInstalled();
        actions.forEach(this::install);
    }

    private void install(KeyStroke key, Runnable action) {
        String name = ACTION_PREFIX + key;
        inputMap().put(key, name);
        scope.getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                action.run();
            }
        });
        installed.add(key);
    }

    private void removeInstalled() {
        for (KeyStroke key : installed) {
            scope.getActionMap().remove(inputMap().get(key));
            inputMap().remove(key);
        }
        installed.clear();
    }

    private InputMap inputMap() {
        return scope.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
    }
}
