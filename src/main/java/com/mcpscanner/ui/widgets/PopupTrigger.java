package com.mcpscanner.ui.widgets;

import javax.swing.JPopupMenu;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

public final class PopupTrigger extends MouseAdapter {

    private final Function<MouseEvent, Optional<JPopupMenu>> menuAt;

    private PopupTrigger(Function<MouseEvent, Optional<JPopupMenu>> menuAt) {
        this.menuAt = Objects.requireNonNull(menuAt, "menuAt");
    }

    public static void install(Component target, Function<MouseEvent, Optional<JPopupMenu>> menuAt) {
        target.addMouseListener(new PopupTrigger(menuAt));
    }

    public static void installThroughout(Component root, Function<MouseEvent, Optional<JPopupMenu>> menuAt) {
        PopupTrigger trigger = new PopupTrigger(menuAt);
        addTo(root, trigger);
    }

    public Optional<JPopupMenu> menuFor(MouseEvent event) {
        return menuAt.apply(event);
    }

    @Override
    public void mousePressed(MouseEvent event) {
        showIfTrigger(event);
    }

    @Override
    public void mouseReleased(MouseEvent event) {
        showIfTrigger(event);
    }

    private void showIfTrigger(MouseEvent event) {
        if (event.isPopupTrigger()) {
            menuFor(event).ifPresent(menu -> menu.show(event.getComponent(), event.getX(), event.getY()));
        }
    }

    private static void addTo(Component component, PopupTrigger trigger) {
        component.addMouseListener(trigger);
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                addTo(child, trigger);
            }
        }
    }
}
