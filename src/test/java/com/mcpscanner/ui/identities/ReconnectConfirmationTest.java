package com.mcpscanner.ui.identities;

import com.mcpscanner.testutil.SwingTree;
import com.mcpscanner.ui.widgets.WrappingText;
import org.junit.jupiter.api.Test;

import javax.swing.JLabel;
import java.util.ArrayList;
import java.util.List;

import static com.mcpscanner.ui.identities.IdentitiesHarness.fromEdt;
import static com.mcpscanner.ui.identities.IdentitiesHarness.onEdt;
import static org.assertj.core.api.Assertions.assertThat;

class ReconnectConfirmationTest {

    private final List<Boolean> choices = new ArrayList<>();

    @Test
    void asksToReconnectAsTheChosenUserAndSaysThePreviousUserStays() throws Exception {
        ReconnectConfirmation confirmation = confirmation();

        List<String> texts = fromEdt(() -> SwingTree.descendants(confirmation).stream()
                .map(component -> component instanceof JLabel label ? label.getText()
                        : component instanceof WrappingText text ? text.getText() : "")
                .filter(text -> !text.isEmpty()).toList());

        assertThat(texts).contains("Reconnect as bob?", "The scanner session will be restarted as bob.",
                "alice becomes a compared user, replayed like any other ticked user.");
    }

    @Test
    void reconnectingTheSameUserDoesNotSayTheyStayInIdentities() throws Exception {
        ReconnectConfirmation confirmation = fromEdt(() -> new ReconnectConfirmation("alice", "alice", choices::add));

        List<String> texts = fromEdt(() -> SwingTree.descendants(confirmation).stream()
                .filter(WrappingText.class::isInstance).map(component -> ((WrappingText) component).getText())
                .toList());

        assertThat(texts).containsExactly("The scanner session will be restarted as alice.");
    }

    @Test
    void cancelAndReconnectAreEqualWidthAndReportTheChoice() throws Exception {
        ReconnectConfirmation confirmation = confirmation();
        onEdt(() -> {
            confirmation.setSize(confirmation.getPreferredSize());
            SwingTree.layOut(confirmation);
        });

        assertThat(fromEdt(() -> confirmation.cancelButton().getSize()))
                .isEqualTo(fromEdt(() -> confirmation.reconnectButton().getSize()));
        assertThat(fromEdt(() -> confirmation.cancelButton().getText())).isEqualTo("Cancel");
        assertThat(fromEdt(() -> confirmation.reconnectButton().getText())).isEqualTo("Reconnect");

        onEdt(() -> confirmation.cancelButton().doClick());
        onEdt(() -> confirmation.reconnectButton().doClick());

        assertThat(choices).containsExactly(false, true);
    }

    private ReconnectConfirmation confirmation() throws Exception {
        return fromEdt(() -> new ReconnectConfirmation("bob", "alice", choices::add));
    }
}
