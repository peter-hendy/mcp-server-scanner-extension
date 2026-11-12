package com.mcpscanner.ui.scans;

import burp.api.montoya.ui.editor.HttpRequestEditor;
import burp.api.montoya.ui.editor.HttpResponseEditor;

import javax.swing.JPanel;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

final class FakeEditors implements MessageEditors {

    final HttpRequestEditor request = mock(HttpRequestEditor.class);
    final HttpResponseEditor response = mock(HttpResponseEditor.class);
    int created;

    FakeEditors() {
        when(request.uiComponent()).thenReturn(new JPanel());
        when(response.uiComponent()).thenReturn(new JPanel());
    }

    @Override
    public HttpRequestEditor requestEditor() {
        created++;
        return request;
    }

    @Override
    public HttpResponseEditor responseEditor() {
        return response;
    }
}
