package com.mcpscanner.ui.engine;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.ui.contextmenu.ContextMenuEvent;
import burp.api.montoya.ui.contextmenu.MessageEditorHttpRequestResponse;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JMenuItem;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class McpOnlyScanMenuTest {

    private final List<List<HttpRequestResponse>> scans = new ArrayList<>();
    private final McpOnlyScanMenu menu = new McpOnlyScanMenu(scans::add);
    private final HttpRequestResponse first = mock(HttpRequestResponse.class);
    private final HttpRequestResponse second = mock(HttpRequestResponse.class);

    @BeforeEach
    void installMontoya() {
        MontoyaTestFactory.install();
    }

    @Test
    void offersOneScanItemForSelectedRequests() {
        JMenuItem item = onlyItem(eventSelecting(List.of(first, second), Optional.empty()));

        assertThat(item.getText()).isEqualTo(McpOnlyScanMenu.ITEM_TEXT);
    }

    @Test
    void clickingScansExactlyTheSelectedRequests() {
        onlyItem(eventSelecting(List.of(first, second), Optional.empty())).doClick();

        assertThat(scans).containsExactly(List.of(first, second));
    }

    @Test
    void clickingScansOnlyTheMessageEditorRequestWhenNothingIsSelected() {
        HttpRequest editedRequest = mock(HttpRequest.class);

        onlyItem(eventSelecting(List.of(), Optional.of(editorShowing(editedRequest)))).doClick();

        assertThat(scans).singleElement().satisfies(scanned -> assertThat(scanned).singleElement()
                .satisfies(base -> {
                    assertThat(base.request()).isSameAs(editedRequest);
                    assertThat(base.response()).isNull();
                }));
    }

    @Test
    void selectedRequestsTakePrecedenceOverTheMessageEditor() {
        MessageEditorHttpRequestResponse editor = editorShowing(mock(HttpRequest.class));

        onlyItem(eventSelecting(List.of(first), Optional.of(editor))).doClick();

        assertThat(scans).containsExactly(List.of(first));
    }

    @Test
    void offersNothingWithoutASelectionOrEditor() {
        assertThat(menu.provideMenuItems(eventSelecting(List.of(), Optional.empty()))).isEmpty();
        assertThat(scans).isEmpty();
    }

    private JMenuItem onlyItem(ContextMenuEvent event) {
        List<Component> items = menu.provideMenuItems(event);
        assertThat(items).singleElement().isInstanceOf(JMenuItem.class);
        return JMenuItem.class.cast(items.get(0));
    }

    private static MessageEditorHttpRequestResponse editorShowing(HttpRequest request) {
        HttpRequestResponse shown = mock(HttpRequestResponse.class);
        when(shown.request()).thenReturn(request);
        when(shown.response()).thenReturn(mock(HttpResponse.class));
        when(shown.hasResponse()).thenReturn(true);
        MessageEditorHttpRequestResponse editor = mock(MessageEditorHttpRequestResponse.class);
        when(editor.requestResponse()).thenReturn(shown);
        return editor;
    }

    private static ContextMenuEvent eventSelecting(List<HttpRequestResponse> selected,
                                                   Optional<MessageEditorHttpRequestResponse> editor) {
        ContextMenuEvent event = mock(ContextMenuEvent.class);
        when(event.selectedRequestResponses()).thenReturn(selected);
        when(event.messageEditorRequestResponse()).thenReturn(editor);
        return event;
    }
}
