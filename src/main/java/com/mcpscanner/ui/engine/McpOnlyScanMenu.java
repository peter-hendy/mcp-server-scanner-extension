package com.mcpscanner.ui.engine;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.ui.contextmenu.ContextMenuEvent;
import burp.api.montoya.ui.contextmenu.ContextMenuItemsProvider;
import burp.api.montoya.ui.contextmenu.MessageEditorHttpRequestResponse;

import javax.swing.JMenuItem;
import java.awt.Component;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public final class McpOnlyScanMenu implements ContextMenuItemsProvider {

    static final String ITEM_TEXT = "Scan with extension checks only";

    private final Consumer<List<HttpRequestResponse>> scanner;

    public McpOnlyScanMenu(Consumer<List<HttpRequestResponse>> scanner) {
        this.scanner = Objects.requireNonNull(scanner, "scanner must not be null");
    }

    @Override
    public List<Component> provideMenuItems(ContextMenuEvent event) {
        List<HttpRequestResponse> targets = targetsOf(event);
        return targets.isEmpty() ? List.of() : List.of(scanItem(targets));
    }

    private JMenuItem scanItem(List<HttpRequestResponse> targets) {
        JMenuItem item = new JMenuItem(ITEM_TEXT);
        item.addActionListener(click -> scanner.accept(targets));
        return item;
    }

    private static List<HttpRequestResponse> targetsOf(ContextMenuEvent event) {
        List<HttpRequestResponse> selected = event.selectedRequestResponses();
        if (!selected.isEmpty()) {
            return List.copyOf(selected);
        }
        return event.messageEditorRequestResponse()
                .map(McpOnlyScanMenu::unsentRequestOf)
                .map(List::of)
                .orElse(List.of());
    }

    private static HttpRequestResponse unsentRequestOf(MessageEditorHttpRequestResponse editor) {
        return HttpRequestResponse.httpRequestResponse(editor.requestResponse().request(), null);
    }
}
