package com.mcpscanner.ui;

import com.mcpscanner.ui.detail.FieldGrid;
import com.mcpscanner.ui.widgets.CopyableSecret;
import com.mcpscanner.ui.widgets.MaxWidthPanel;
import com.mcpscanner.ui.widgets.WrappingText;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.event.HierarchyEvent;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;

final class AuthenticationOverview extends JPanel {

    static final String ACCESS_TOKEN = "Access token";
    static final String REFRESH_TOKEN = "Refresh token";
    static final String CLIENT_MANAGEMENT = "Client management";
    static final int MAX_WIDTH = 580;

    private final Clock clock;
    private final AccessTokenView tokenView;
    private final ClientManagementView clientManagement;
    private final CopyableSecret refreshToken;
    private Optional<AuthenticationSummary> summary = Optional.empty();
    private FieldGrid grid = new FieldGrid();

    AuthenticationOverview(Clock clock, AccessTokenView tokenView, CopyableSecret refreshToken,
                           ClientManagementView clientManagement) {
        super(new BorderLayout());
        this.clock = clock;
        this.tokenView = tokenView;
        this.refreshToken = refreshToken;
        this.clientManagement = clientManagement;
        setOpaque(false);
        addHierarchyListener(this::refreshWhenShown);
    }

    void show(AuthenticationSummary shown) {
        summary = Optional.of(shown);
        refresh();
    }

    void clear() {
        summary = Optional.empty();
        removeAll();
    }

    boolean hasSummary() {
        return summary.isPresent();
    }

    FieldGrid grid() {
        return grid;
    }

    void refresh() {
        summary.ifPresent(this::render);
    }

    private void render(AuthenticationSummary shown) {
        tokenView.show(shown, this::refresh);
        refreshToken.show(shown.refreshToken());
        clientManagement.show(shown.clientManagement());
        Map<String, String> rows = shown.rows(clock);
        FieldGrid rendered = new FieldGrid();
        rows.forEach((label, value) -> {
            if (label.equals(AuthenticationSummary.TOKEN_EXPIRES)) {
                rendered.addComponent(label, expiryWithRenewal(value));
                addTokenView(rendered);
            } else {
                rendered.addText(label, value);
            }
        });
        if (!rows.containsKey(AuthenticationSummary.TOKEN_EXPIRES)) {
            addTokenView(rendered);
        }
        if (refreshToken.isVisible()) {
            rendered.addComponent(REFRESH_TOKEN, refreshToken);
        }
        if (clientManagement.isVisible()) {
            rendered.addComponent(CLIENT_MANAGEMENT, clientManagement);
        }
        grid = rendered;
        removeAll();
        add(new MaxWidthPanel(rendered, MAX_WIDTH), BorderLayout.CENTER);
        revalidate();
        repaint();
    }

    private void addTokenView(FieldGrid rendered) {
        if (tokenView.row().isVisible()) {
            rendered.addComponent(ACCESS_TOKEN, tokenView.row());
        }
    }

    private JComponent expiryWithRenewal(String expiry) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.add(new WrappingText(expiry), BorderLayout.CENTER);
        if (tokenView.renewable()) {
            row.add(tokenView.refreshButton(), BorderLayout.EAST);
        }
        return row;
    }

    private void refreshWhenShown(HierarchyEvent event) {
        if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && isShowing()) {
            refresh();
        }
    }
}
