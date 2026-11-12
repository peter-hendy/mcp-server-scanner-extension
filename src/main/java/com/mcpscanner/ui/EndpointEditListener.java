package com.mcpscanner.ui;

import com.mcpscanner.ui.connect.EndpointUrls;

import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;

final class EndpointEditListener implements DocumentListener {

    interface Handler {
        void endpointEdited(String endpoint, boolean hostChanged);
    }

    private final JTextComponent field;
    private final Handler handler;
    private String previousHost;

    EndpointEditListener(JTextComponent field, Handler handler) {
        this.field = field;
        this.handler = handler;
        this.previousHost = hostOf(field.getText().trim());
    }

    @Override
    public void insertUpdate(DocumentEvent e) {
        onChange();
    }

    @Override
    public void removeUpdate(DocumentEvent e) {
        onChange();
    }

    @Override
    public void changedUpdate(DocumentEvent e) {
        onChange();
    }

    private void onChange() {
        String endpoint = field.getText().trim();
        String currentHost = hostOf(endpoint);
        boolean hostChanged = !currentHost.equals(previousHost);
        previousHost = currentHost;
        handler.endpointEdited(endpoint, hostChanged);
    }

    private static String hostOf(String endpoint) {
        return EndpointUrls.hostOr(endpoint, "");
    }
}
