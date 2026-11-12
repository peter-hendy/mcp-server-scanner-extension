package com.mcpscanner.ui.sendto;

import com.mcpscanner.ui.widgets.Blockers;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.KeyStroke;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class SendToMenu {

    private final Supplier<List<SendToDestination>> destinations;
    private final Supplier<Optional<String>> unavailableReason;
    private final Consumer<String> errorReporter;
    private final Supplier<SendToShortcuts> shortcuts;

    public SendToMenu(Supplier<List<SendToDestination>> destinations,
                      Supplier<Optional<String>> unavailableReason,
                      Consumer<String> errorReporter,
                      Supplier<SendToShortcuts> shortcuts) {
        this.destinations = Objects.requireNonNull(destinations);
        this.unavailableReason = Objects.requireNonNull(unavailableReason);
        this.errorReporter = Objects.requireNonNull(errorReporter);
        this.shortcuts = Objects.requireNonNull(shortcuts);
    }

    public Optional<String> unavailableReason() {
        return unavailableReason.get();
    }

    public JPopupMenu popupFor(SendToRequest request) {
        Optional<String> reason = unavailableReason.get();
        SendToShortcuts keys = shortcuts.get();
        JPopupMenu popup = new JPopupMenu();
        for (SendToDestination destination : destinations.get()) {
            popup.add(itemFor(destination, request, reason, keys));
        }
        return popup;
    }

    Map<KeyStroke, SendToDestination> destinationsByShortcut() {
        SendToShortcuts keys = shortcuts.get();
        Map<KeyStroke, SendToDestination> byShortcut = new LinkedHashMap<>();
        for (SendToDestination destination : destinations.get()) {
            keys.keyStrokeFor(destination.name()).ifPresent(key -> byShortcut.putIfAbsent(key, destination));
        }
        return byShortcut;
    }

    void sendIfAvailable(SendToDestination destination, SendToRequest request) {
        if (unavailableReason.get().isEmpty()) {
            send(destination, request);
        }
    }

    private JMenuItem itemFor(SendToDestination destination, SendToRequest request, Optional<String> reason,
                              SendToShortcuts keys) {
        JMenuItem item = new JMenuItem("Send to " + destination.name());
        Blockers.apply(item, reason, null);
        keys.keyStrokeFor(destination.name()).ifPresent(item::setAccelerator);
        item.addActionListener(event -> send(destination, request));
        return item;
    }

    private void send(SendToDestination destination, SendToRequest request) {
        try {
            destination.send(request.request().get(), request.title());
        } catch (RuntimeException failure) {
            errorReporter.accept("Send to " + destination.name() + " failed: " + failure.getClass().getSimpleName());
        }
    }
}
