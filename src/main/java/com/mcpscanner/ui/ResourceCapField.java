package com.mcpscanner.ui;

import com.mcpscanner.config.ExtensionConfigStore;
import com.mcpscanner.config.ResourceCap;

import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.util.OptionalLong;
import java.util.function.Consumer;

final class ResourceCapField {
    private final ResourceCap cap;
    private final ExtensionConfigStore configStore;
    private final Consumer<Runnable> saving;
    private final CapValueText text;
    private final JTextField field = new JTextField(16);
    private final JLabel status = new JLabel(" ");
    private boolean refreshing;

    ResourceCapField(ResourceCap cap, ExtensionConfigStore configStore, Consumer<Runnable> saving) {
        this.cap = cap;
        this.configStore = configStore;
        this.saving = saving;
        this.text = CapValueText.of(cap.unit());
        refresh();
        field.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { save(); }
            @Override public void removeUpdate(DocumentEvent event) { save(); }
            @Override public void changedUpdate(DocumentEvent event) { save(); }
        });
    }

    ResourceCap cap() {
        return cap;
    }

    JTextField field() {
        return field;
    }

    JLabel status() {
        return status;
    }

    void refresh() {
        refreshing = true;
        try {
            String value = text.format(configStore.resourceCap(cap));
            if (!sameValue(field.getText(), configStore.resourceCap(cap))) {
                field.setText(value);
            }
            status.setText(" ");
        } finally {
            refreshing = false;
        }
    }

    private boolean sameValue(String shown, long stored) {
        OptionalLong parsed = text.parse(shown);
        return parsed.isPresent() && parsed.getAsLong() == stored;
    }

    private void save() {
        if (refreshing) return;
        saving.accept(this::persist);
    }

    private void persist() {
        String typed = field.getText().trim();
        OptionalLong value = text.parse(typed);
        if (!typed.isEmpty() && (value.isEmpty() || !cap.accepts(value.getAsLong()))) {
            status.setText(text.invalidHint(cap));
            return;
        }
        configStore.setResourceCap(cap, typed.isEmpty() ? null : value.getAsLong());
        status.setText(" ");
    }
}
