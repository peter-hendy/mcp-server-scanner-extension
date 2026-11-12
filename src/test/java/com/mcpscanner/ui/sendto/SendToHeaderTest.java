package com.mcpscanner.ui.sendto;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.testutil.SwingTree;
import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.JMenuItem;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SendToHeaderTest {

    private final List<String> sent = new ArrayList<>();
    private Optional<String> unavailableReason = Optional.empty();
    private final SendToBinding<String> binding = new SendToBinding<>(
            new SendToMenu(() -> List.of(SendToDestination.of("Alpha", (request, title) -> sent.add(title))),
                    () -> unavailableReason, message -> {}, SendToShortcuts::defaults),
            name -> new SendToRequest("Tool: " + name, () -> mock(HttpRequest.class)));

    @Test
    void buttonIsDisabledWithReasonWhenUnavailable() {
        unavailableReason = Optional.of("Connect first");
        SendToHeader<String> header = new SendToHeader<>();
        header.bind(binding);

        header.show("echo");

        assertThat(button(header).isEnabled()).isFalse();
        assertThat(button(header).getToolTipText()).isEqualTo("Connect first");
    }

    @Test
    void buttonDropdownSendsTheShownItem() {
        SendToHeader<String> header = new SendToHeader<>();
        header.bind(binding);
        header.show("echo");

        assertThat(button(header).isEnabled()).isTrue();
        ((JMenuItem) header.dropdown().orElseThrow().getComponent(0)).doClick();

        assertThat(sent).containsExactly("Tool: echo");
    }

    @Test
    void unboundHeaderHasDisabledButton() {
        SendToHeader<String> header = new SendToHeader<>();

        header.show("echo");

        assertThat(button(header).isEnabled()).isFalse();
    }

    private static JButton button(SendToHeader<?> header) {
        return SwingTree.descendants(header).stream()
                .filter(JButton.class::isInstance).map(JButton.class::cast).findFirst().orElseThrow();
    }
}
