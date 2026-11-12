package com.mcpscanner.ui.sendto;

import com.mcpscanner.ui.widgets.Blockers;
import com.mcpscanner.ui.widgets.PrimaryActionButton;

import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import java.awt.Component;
import java.awt.FlowLayout;
import java.util.Optional;

public final class SendToHeader<T> extends JPanel {

    private static final String AUTHENTICATION_HINT = "How authentication is added to sent requests is set in the"
            + " Settings tab on the right, or in Burp Settings under Extensions > MCP Server Scanner.";

    private final PrimaryActionButton button = new PrimaryActionButton("Send to ▾");
    private SendToBinding<T> binding;
    private T item;

    public SendToHeader() {
        super(new FlowLayout(FlowLayout.LEFT, 0, 0));
        setOpaque(false);
        setAlignmentX(Component.LEFT_ALIGNMENT);
        add(button);
        button.setPrimary(false);
        button.addActionListener(event -> openDropdown());
        refresh();
    }

    public void bind(SendToBinding<T> binding) {
        this.binding = binding;
        refresh();
    }

    public void show(T item) {
        this.item = item;
        refresh();
    }

    public void refresh() {
        Optional<String> reason = binding == null
                ? Optional.of("Send to is not available")
                : binding.unavailableReason();
        Blockers.apply(button, reason, AUTHENTICATION_HINT);
    }

    private void openDropdown() {
        dropdown().ifPresent(menu -> menu.show(button, 0, button.getHeight()));
    }

    Optional<JPopupMenu> dropdown() {
        return binding == null || item == null ? Optional.empty() : Optional.of(binding.popupFor(item));
    }
}
