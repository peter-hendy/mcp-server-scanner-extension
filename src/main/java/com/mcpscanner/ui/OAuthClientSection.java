package com.mcpscanner.ui;

import com.mcpscanner.ui.widgets.HyperlinkLabel;
import com.mcpscanner.ui.widgets.WrappingText;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Rectangle;

final class OAuthClientSection extends JPanel {

    static final String HEADING = "OAuth client";
    static final String INTRO = "Used by every user who signs in with OAuth, and by Detect automatically "
            + "when the server advertises OAuth.";
    static final String LOCKED = "Locked while connected. ";

    private final JPanel lockNotice;

    OAuthClientSection(ServerConfigPanel form, Runnable disconnect) {
        super(new BorderLayout());
        this.lockNotice = lockNotice(disconnect);
        JPanel header = new JPanel(new BorderLayout(0, 4));
        header.setBorder(BorderFactory.createEmptyBorder(8, 8, 4, 8));
        header.add(heading(), BorderLayout.NORTH);
        header.add(new WrappingText(INTRO).muted(), BorderLayout.CENTER);
        header.add(lockNotice, BorderLayout.SOUTH);
        add(header, BorderLayout.NORTH);
        add(form, BorderLayout.CENTER);
        setLocked(false);
    }

    void setLocked(boolean locked) {
        lockNotice.setVisible(locked);
    }

    boolean showsLockNotice() {
        return lockNotice.isVisible();
    }

    void reveal() {
        scrollRectToVisible(new Rectangle(0, 0, getWidth(), getHeight()));
    }

    private static JLabel heading() {
        JLabel heading = new JLabel(HEADING);
        heading.setFont(heading.getFont().deriveFont(Font.BOLD));
        return heading;
    }

    private static JPanel lockNotice(Runnable disconnect) {
        HyperlinkLabel link = new HyperlinkLabel("Disconnect");
        link.onActivate(disconnect);
        link.setToolTipText("Disconnect from the MCP server so the OAuth client can be edited");
        JPanel notice = new JPanel(new FlowLayout(FlowLayout.LEADING, 0, 0));
        notice.setOpaque(false);
        notice.add(new JLabel(LOCKED));
        notice.add(link);
        notice.add(new JLabel(" to change."));
        return notice;
    }
}
