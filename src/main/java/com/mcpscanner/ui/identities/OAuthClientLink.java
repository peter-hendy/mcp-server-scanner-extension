package com.mcpscanner.ui.identities;

import com.mcpscanner.identity.Identity;
import com.mcpscanner.identity.IdentityAuthKind;
import com.mcpscanner.ui.widgets.HyperlinkLabel;
import com.mcpscanner.ui.widgets.WrapLayout;

import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.FlowLayout;

final class OAuthClientLink extends JPanel {

    static final String TEXT = "Signs in through the ";
    static final String LINK = "OAuth client in the Client tab";

    private final HyperlinkLabel link = new HyperlinkLabel(LINK);

    OAuthClientLink(Runnable openOAuthClient) {
        super(new WrapLayout(FlowLayout.LEADING, 0, 0));
        setOpaque(false);
        link.onActivate(openOAuthClient);
        link.setToolTipText("Open the Client tab at the OAuth client");
        add(new JLabel(TEXT));
        add(link);
    }

    static boolean appliesTo(Identity identity) {
        return identity.authKind() == IdentityAuthKind.DETECT
                || identity.authKind() == IdentityAuthKind.OAUTH && identity.oauth().issuer().isBlank();
    }

    HyperlinkLabel link() {
        return link;
    }
}
