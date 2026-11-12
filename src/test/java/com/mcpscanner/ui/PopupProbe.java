package com.mcpscanner.ui;

import com.mcpscanner.ui.completion.CompletionMenus;
import com.mcpscanner.ui.detail.RowMenu;
import com.mcpscanner.ui.widgets.PopupTrigger;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import java.awt.Component;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.Optional;

final class PopupProbe {

    static final CompletionMenus LABELLING_MENUS = new CompletionMenus() {
        @Override
        public RowMenu forPromptArguments(String promptName) {
            return argument -> Optional.of(menuLabelled("prompt " + promptName + " / " + argument));
        }

        @Override
        public RowMenu forTemplateVariables(String uriTemplate) {
            return variable -> Optional.of(menuLabelled("template " + uriTemplate + " / " + variable));
        }
    };

    private PopupProbe() {
    }

    static Optional<String> rightClickLabel(Component component, int x, int y) {
        PopupTrigger trigger = Arrays.stream(component.getMouseListeners())
                .filter(PopupTrigger.class::isInstance).map(PopupTrigger.class::cast)
                .findFirst().orElseThrow(() -> new AssertionError("no popup trigger on " + component));
        return trigger.menuFor(new MouseEvent(component, MouseEvent.MOUSE_PRESSED, 0, 0, x, y, 1, true,
                        MouseEvent.BUTTON3))
                .map(menu -> ((JMenuItem) menu.getComponent(0)).getText());
    }

    private static JPopupMenu menuLabelled(String label) {
        JPopupMenu menu = new JPopupMenu();
        menu.add(new JMenuItem(label));
        return menu;
    }
}
