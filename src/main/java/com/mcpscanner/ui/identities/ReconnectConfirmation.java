package com.mcpscanner.ui.identities;

import com.mcpscanner.ui.widgets.PrimaryActionButton;
import com.mcpscanner.ui.widgets.WrappingText;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.function.Consumer;

final class ReconnectConfirmation extends JPanel {

    private static final int MESSAGE_WIDTH = 380;

    private final JButton cancel = new JButton("Cancel");
    private final JButton reconnect = new PrimaryActionButton("Reconnect");

    ReconnectConfirmation(String user, String previousUser, Consumer<Boolean> choice) {
        super(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(18, 20, 16, 20));
        cancel.addActionListener(event -> choice.accept(false));
        reconnect.addActionListener(event -> choice.accept(true));
        add(messages(user, previousUser).add(buttons()), BorderLayout.NORTH);
    }

    JButton cancelButton() {
        return cancel;
    }

    JButton reconnectButton() {
        return reconnect;
    }

    private static VerticalStack messages(String user, String previousUser) {
        VerticalStack messages = new VerticalStack(8)
                .add(title("Reconnect as " + user + "?"))
                .add(paragraph(new WrappingText("The scanner session will be restarted as " + user + ".")));
        if (!user.equals(previousUser)) {
            messages.add(paragraph(new WrappingText(previousUser
                    + " becomes a compared user, replayed like any other ticked user.").muted()));
        }
        return messages;
    }

    private JPanel buttons() {
        JPanel pair = new JPanel(new GridLayout(1, 2, 8, 0));
        pair.setOpaque(false);
        pair.add(cancel);
        pair.add(reconnect);
        JPanel row = new JPanel(new FlowLayout(FlowLayout.TRAILING, 0, 6));
        row.setOpaque(false);
        row.add(pair);
        return row;
    }

    private static JLabel title(String text) {
        JLabel title = new JLabel(text);
        title.setFont(title.getFont().deriveFont(Font.BOLD, title.getFont().getSize2D() + 2));
        return title;
    }

    private static WrappingText paragraph(WrappingText text) {
        text.setSize(new Dimension(MESSAGE_WIDTH, Short.MAX_VALUE));
        text.setPreferredSize(new Dimension(MESSAGE_WIDTH, text.getPreferredSize().height));
        return text;
    }
}
