package com.mcpscanner.ui.sendto;

import javax.swing.JComponent;
import javax.swing.JPopupMenu;
import javax.swing.KeyStroke;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

public final class SendToBinding<T> {

    private final SendToMenu menu;
    private final Function<T, SendToRequest> requestFor;
    private SendToShortcutKeys shortcutKeys;
    private Supplier<Optional<T>> selection = Optional::empty;

    public SendToBinding(SendToMenu menu, Function<T, SendToRequest> requestFor) {
        this.menu = Objects.requireNonNull(menu);
        this.requestFor = Objects.requireNonNull(requestFor);
    }

    public JPopupMenu popupFor(T item) {
        return menu.popupFor(requestFor.apply(item));
    }

    public Optional<String> unavailableReason() {
        return menu.unavailableReason();
    }

    public void bindShortcuts(JComponent scope, Supplier<Optional<T>> selection) {
        this.shortcutKeys = new SendToShortcutKeys(scope);
        this.selection = Objects.requireNonNull(selection);
        refreshShortcuts();
    }

    public void refreshShortcuts() {
        if (shortcutKeys != null) {
            shortcutKeys.replace(shortcutActions());
        }
    }

    private Map<KeyStroke, Runnable> shortcutActions() {
        Map<KeyStroke, Runnable> actions = new LinkedHashMap<>();
        menu.destinationsByShortcut().forEach((key, destination) ->
                actions.put(key, () -> sendSelectedTo(destination)));
        return actions;
    }

    private void sendSelectedTo(SendToDestination destination) {
        selection.get().ifPresent(item -> menu.sendIfAvailable(destination, requestFor.apply(item)));
    }
}
