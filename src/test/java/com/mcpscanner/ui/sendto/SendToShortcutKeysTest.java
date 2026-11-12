package com.mcpscanner.ui.sendto;

import burp.api.montoya.http.message.requests.HttpRequest;
import org.junit.jupiter.api.Test;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SendToShortcutKeysTest {

    private static final KeyStroke REPEATER_KEY = KeyStroke.getKeyStroke(KeyEvent.VK_R, BurpHotkey.shortcutMask());
    private static final KeyStroke COMPARER_KEY = KeyStroke.getKeyStroke(KeyEvent.VK_M, BurpHotkey.shortcutMask());

    private final List<String> sent = new ArrayList<>();
    private final JPanel scope = new JPanel();
    private Optional<String> unavailableReason = Optional.empty();
    private Optional<String> selected = Optional.of("echo");
    private String hotkeys = "";

    @Test
    void theRepeaterHotkeySendsTheSelectedItemThroughTheMenu() {
        bound();

        press(REPEATER_KEY);

        assertThat(sent).containsExactly("Repeater:Tool: echo");
    }

    @Test
    void destinationsWithoutAHotkeyAreNotBound() {
        bound();

        assertThat(scope.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).get(COMPARER_KEY)).isNull();
    }

    @Test
    void nothingIsSentWithoutASelectedItemOrWhileSendToIsUnavailable() {
        SendToBinding<String> binding = bound();
        selected = Optional.empty();
        press(REPEATER_KEY);
        selected = Optional.of("echo");
        unavailableReason = Optional.of("Connect first");
        binding.refreshShortcuts();
        press(REPEATER_KEY);

        assertThat(sent).isEmpty();
    }

    @Test
    void refreshingPicksUpChangedBurpHotkeysAndDropsTheOldOnes() {
        SendToBinding<String> binding = bound();
        hotkeys = """
                {"user_options":{"misc":{"hotkeys":[{"action":"send_to_repeater","hotkey":"Ctrl+M"}]}}}""";

        binding.refreshShortcuts();

        assertThat(scope.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).get(REPEATER_KEY)).isNull();
        press(COMPARER_KEY);
        assertThat(sent).containsExactly("Repeater:Tool: echo");
    }

    private SendToBinding<String> bound() {
        SendToMenu menu = new SendToMenu(() -> List.of(destination("Repeater"), destination("Comparer")),
                () -> unavailableReason, error -> { }, () -> new SendToShortcuts(() -> hotkeys));
        SendToBinding<String> binding = new SendToBinding<>(menu,
                name -> new SendToRequest("Tool: " + name, () -> mock(HttpRequest.class)));
        binding.bindShortcuts(scope, () -> selected);
        return binding;
    }

    private SendToDestination destination(String name) {
        return SendToDestination.of(name, (request, title) -> sent.add(name + ":" + title));
    }

    private void press(KeyStroke key) {
        Object actionKey = scope.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).get(key);
        if (actionKey != null) {
            scope.getActionMap().get(actionKey).actionPerformed(new ActionEvent(scope, ActionEvent.ACTION_PERFORMED, ""));
        }
    }
}
