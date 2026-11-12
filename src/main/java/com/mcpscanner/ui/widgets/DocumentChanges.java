package com.mcpscanner.ui.widgets;

import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

public final class DocumentChanges {

    private DocumentChanges() {
    }

    public static DocumentListener onChange(Runnable action) {
        return new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { action.run(); }
            @Override public void removeUpdate(DocumentEvent event) { action.run(); }
            @Override public void changedUpdate(DocumentEvent event) { action.run(); }
        };
    }
}
