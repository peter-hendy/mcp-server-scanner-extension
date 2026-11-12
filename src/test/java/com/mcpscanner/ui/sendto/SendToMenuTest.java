package com.mcpscanner.ui.sendto;

import burp.api.montoya.http.message.requests.HttpRequest;
import org.junit.jupiter.api.Test;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.KeyStroke;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SendToMenuTest {

    private final HttpRequest built = mock(HttpRequest.class);
    private final List<String> sent = new ArrayList<>();
    private final List<String> errors = new ArrayList<>();
    private final List<SendToDestination> destinations = List.of(
            SendToDestination.of("Alpha", (request, title) -> sent.add("Alpha:" + title + ":" + (request == built))),
            SendToDestination.of("Beta", (request, title) -> sent.add("Beta:" + title + ":" + (request == built))));
    private Optional<String> unavailableReason = Optional.empty();
    private SendToShortcuts shortcuts = SendToShortcuts.defaults();

    @Test
    void listsExactlyTheRegisteredDestinations() {
        assertThat(texts(menu().popupFor(request()))).containsExactly("Send to Alpha", "Send to Beta");
    }

    @Test
    void clickingAnItemSendsTheBuiltRequestWithTitle() {
        JPopupMenu popup = menu().popupFor(request());

        item(popup, 1).doClick();

        assertThat(sent).containsExactly("Beta:Tool: echo:true");
    }

    @Test
    void itemsAreDisabledWithReasonTooltipWhenUnavailable() {
        unavailableReason = Optional.of("Connect first");
        JPopupMenu popup = menu().popupFor(request());

        assertThat(item(popup, 0).isEnabled()).isFalse();
        assertThat(item(popup, 0).getToolTipText()).isEqualTo("Connect first");
        item(popup, 0).doClick();
        assertThat(sent).isEmpty();
    }

    @Test
    void requestIsOnlyBuiltWhenAnItemIsClicked() {
        List<String> builds = new ArrayList<>();
        Supplier<HttpRequest> counting = () -> {
            builds.add("built");
            return built;
        };

        JPopupMenu popup = menu().popupFor(new SendToRequest("t", counting));

        assertThat(builds).isEmpty();
        item(popup, 0).doClick();
        assertThat(builds).hasSize(1);
    }

    @Test
    void itemsShowTheirBurpHotkeyAndItemsWithoutOneShowNone() {
        shortcuts = new SendToShortcuts(() -> """
                {"user_options":{"misc":{"hotkeys":[{"action":"send_to_alpha","hotkey":"Ctrl+R"}]}}}""");

        JPopupMenu popup = menu().popupFor(request());

        assertThat(item(popup, 0).getAccelerator())
                .isEqualTo(KeyStroke.getKeyStroke(KeyEvent.VK_R, BurpHotkey.shortcutMask()));
        assertThat(item(popup, 1).getAccelerator()).isNull();
    }

    @Test
    void destinationFailureIsReportedAndDoesNotPropagate() {
        SendToMenu failing = new SendToMenu(
                () -> List.of(SendToDestination.of("Boom", (request, title) -> {
                    throw new IllegalStateException("nope");
                })),
                () -> Optional.empty(), errors::add, SendToShortcuts::defaults);

        item(failing.popupFor(request()), 0).doClick();

        assertThat(errors).containsExactly("Send to Boom failed: IllegalStateException");
    }

    @Test
    void bindingBuildsTheSameMenuForAnItem() {
        SendToBinding<String> binding = new SendToBinding<>(menu(),
                name -> new SendToRequest("Tool: " + name, () -> built));

        item(binding.popupFor("echo"), 0).doClick();

        assertThat(sent).containsExactly("Alpha:Tool: echo:true");
        assertThat(binding.unavailableReason()).isEmpty();
    }

    private SendToMenu menu() {
        return new SendToMenu(() -> destinations, () -> unavailableReason, errors::add, () -> shortcuts);
    }

    private SendToRequest request() {
        return new SendToRequest("Tool: echo", () -> built);
    }

    private static JMenuItem item(JPopupMenu popup, int index) {
        return (JMenuItem) popup.getComponent(index);
    }

    private static List<String> texts(JPopupMenu popup) {
        List<String> texts = new ArrayList<>();
        for (java.awt.Component component : popup.getComponents()) {
            texts.add(((JMenuItem) component).getText());
        }
        return texts;
    }
}
