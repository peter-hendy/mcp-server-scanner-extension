package com.mcpscanner.ui.scans;

import burp.api.montoya.ui.UserInterface;
import burp.api.montoya.ui.editor.EditorOptions;
import burp.api.montoya.ui.editor.HttpRequestEditor;
import burp.api.montoya.ui.editor.HttpResponseEditor;

public interface MessageEditors {

    static MessageEditors readOnly(UserInterface userInterface) {
        return new MessageEditors() {
            @Override
            public HttpRequestEditor requestEditor() {
                return userInterface.createHttpRequestEditor(EditorOptions.READ_ONLY);
            }

            @Override
            public HttpResponseEditor responseEditor() {
                return userInterface.createHttpResponseEditor(EditorOptions.READ_ONLY);
            }
        };
    }

    HttpRequestEditor requestEditor();

    HttpResponseEditor responseEditor();
}
