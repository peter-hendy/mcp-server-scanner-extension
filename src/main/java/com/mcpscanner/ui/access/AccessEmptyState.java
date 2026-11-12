package com.mcpscanner.ui.access;

import com.mcpscanner.ui.widgets.HyperlinkLabel;
import com.mcpscanner.ui.widgets.WrappingText;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagLayout;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

final class AccessEmptyState extends JPanel {

    static final String TITLE = "Compare what each user can reach";
    static final String BODY = "This view lists every tool and resource against each identity: whether that identity "
            + "sees it in its own tools/list, and whether calling it as that identity succeeds.\n\n"
            + "Only the primary user exists so far. Add at least one more user to compare.";
    private static final int TEXT_WIDTH = 480;

    private final JButton addPeer = new JButton("Add peer…");
    private final JButton addLower = new JButton("Add lower-privilege user…");
    private final HyperlinkLabel openIdentities = new HyperlinkLabel("Open Users");
    private Optional<IdentityShortcuts> shortcuts = Optional.empty();

    AccessEmptyState() {
        super(new GridBagLayout());
        openIdentities.onActivate(() -> run(IdentityShortcuts::openIdentities));
        addPeer.addActionListener(event -> run(IdentityShortcuts::addPeer));
        addLower.addActionListener(event -> run(IdentityShortcuts::addLowerPrivilegeUser));
        add(content());
        enableShortcuts();
    }

    void setShortcuts(IdentityShortcuts value) {
        shortcuts = Optional.of(value);
        enableShortcuts();
    }

    JButton addPeerButton() {
        return addPeer;
    }

    JButton addLowerButton() {
        return addLower;
    }

    HyperlinkLabel openIdentitiesLink() {
        return openIdentities;
    }

    private void run(Consumer<IdentityShortcuts> shortcut) {
        shortcuts.ifPresent(shortcut);
    }

    private void enableShortcuts() {
        List.of(addPeer, addLower, openIdentities).forEach(control -> control.setEnabled(shortcuts.isPresent()));
    }

    private JComponent content() {
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        JLabel title = new JLabel(TITLE);
        title.setFont(title.getFont().deriveFont(Font.BOLD, title.getFont().getSize() + 3f));
        WrappingText body = new WrappingText(BODY).muted();
        body.setSize(new Dimension(TEXT_WIDTH, Short.MAX_VALUE));
        body.setPreferredSize(new Dimension(TEXT_WIDTH, body.getPreferredSize().height));
        for (JComponent component : List.of(title, body, buttons())) {
            component.setAlignmentX(Component.LEFT_ALIGNMENT);
            content.add(component);
            content.add(Box.createVerticalStrut(12));
        }
        return content;
    }

    private JComponent buttons() {
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEADING, 0, 0));
        buttons.add(addPeer);
        buttons.add(Box.createHorizontalStrut(8));
        buttons.add(addLower);
        buttons.add(Box.createHorizontalStrut(16));
        buttons.add(openIdentities);
        return buttons;
    }
}
