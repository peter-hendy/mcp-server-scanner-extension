package com.mcpscanner.ui;

import com.mcpscanner.scan.ScanMode;
import com.mcpscanner.ui.widgets.Blockers;
import com.mcpscanner.ui.widgets.PrimaryActionButton;

import java.util.Optional;
import java.util.function.Supplier;

final class ScanItemButton<T> {

    private static final String UNAVAILABLE = "Scanning is not available";

    private final PrimaryActionButton button;
    private final String noun;
    private ScanItemBinding<T> binding;
    private Supplier<ScanMode> mode;
    private T item;

    ScanItemButton(String noun) {
        this.noun = noun;
        this.button = new PrimaryActionButton("Scan this " + noun);
        button.addActionListener(event -> scan());
        refresh();
    }

    PrimaryActionButton button() {
        return button;
    }

    void bind(ScanItemBinding<T> binding, Supplier<ScanMode> mode) {
        this.binding = binding;
        this.mode = mode;
        refresh();
    }

    void show(T item) {
        this.item = item;
        refresh();
    }

    void refresh() {
        Optional<String> reason = blocker();
        Blockers.apply(button, reason, reason.isEmpty() ? availableTooltip() : null);
        button.setPrimary(reason.isEmpty());
    }

    private String availableTooltip() {
        return "Scan only this " + noun + " with " + ScanModeText.of(mode.get()).label();
    }

    private Optional<String> blocker() {
        if (binding == null || item == null) {
            return Optional.of(UNAVAILABLE);
        }
        return MalformedRows.scanBlocker(item, noun).or(() -> binding.blocker(item));
    }

    private void scan() {
        if (blocker().isEmpty()) {
            binding.scan(item);
        }
    }
}
