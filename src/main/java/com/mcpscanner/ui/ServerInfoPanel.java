package com.mcpscanner.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.mcpscanner.mcp.McpObjectMapper;
import com.mcpscanner.mcp.ServerMetadata;
import com.mcpscanner.ui.detail.DetailSection;
import com.mcpscanner.ui.detail.DetailTabs;
import com.mcpscanner.ui.detail.FieldGrid;
import com.mcpscanner.ui.detail.RawJsonView;
import com.mcpscanner.ui.serverinfo.AdvertisedVersions;
import com.mcpscanner.ui.serverinfo.AuthDiscoveryReport;
import com.mcpscanner.ui.serverinfo.AuthDiscoveryView;
import com.mcpscanner.ui.serverinfo.CapabilityCards;
import com.mcpscanner.ui.serverinfo.ProtocolVersionView;
import com.mcpscanner.ui.serverinfo.ServerCardSource;
import com.mcpscanner.ui.serverinfo.ServerCardView;
import com.mcpscanner.ui.serverinfo.ServerIcons;
import com.mcpscanner.ui.serverinfo.ServerIdentityView;
import com.mcpscanner.ui.widgets.Placeholders;
import com.mcpscanner.ui.widgets.ScrollableForm;
import com.mcpscanner.ui.widgets.WrappingText;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;

public class ServerInfoPanel extends JPanel {

    private static final String OVERVIEW = "Overview";
    private static final String SERVER_CARD = "Server card";
    private static final String AUTH_DISCOVERY = "Auth discovery";
    private static final String RAW_HINT = "The whole initialize result as the server sent it. Key order is kept.";
    private static final String EMPTY_CONNECTION = "Not connected.";
    private static final String NO_SESSION = "none";
    private static final int PADDING = 8;
    private static final int SECTION_GAP = 16;

    private final DetailTabs tabs = new DetailTabs();
    private final RawJsonView raw = new RawJsonView(RAW_HINT);
    private final JPanel identity = new JPanel(new BorderLayout());
    private final DetailSection session = new DetailSection("Session");
    private final DetailSection authentication = new DetailSection("Authentication");
    private final DetailSection capabilities = new DetailSection("Capabilities");
    private final DetailSection instructions = new DetailSection("Instructions");
    private final JComponent overview;
    private final ServerCardView serverCards;
    private final AuthDiscoveryView authDiscovery = new AuthDiscoveryView(ZoneId.systemDefault());
    private boolean hasRaw;
    private ConnectionSummary connection;
    private String advertisedVersions = AdvertisedVersions.of(null);

    public ServerInfoPanel() {
        this(ServerCardView.inBackground());
    }

    ServerInfoPanel(ServerCardView serverCards) {
        super(new BorderLayout());
        this.serverCards = serverCards;
        identity.setOpaque(false);
        identity.setAlignmentX(Component.LEFT_ALIGNMENT);
        overview = ScrollableForm.wrap(topAligned(overviewStack()));
        add(tabs.component(), BorderLayout.CENTER);
        populate(ServerMetadata.empty());
        clearConnection();
    }

    public void showConnection(ConnectionSummary summary) {
        connection = summary;
        ConnectedEndpoint.of(summary.endpoint()).ifPresentOrElse(serverCards::enableFor, serverCards::disconnect);
        showSession();
        rebuildTabs();
    }

    public void showAuthentication(JComponent view) {
        authentication.show(view);
        authentication.setVisible(true);
    }

    public void clearConnection() {
        connection = null;
        authentication.setVisible(false);
        serverCards.disconnect();
        authDiscovery.show(AuthDiscoveryReport.none());
        showSession();
        rebuildTabs();
    }

    public void showAuthDiscovery(AuthDiscoveryReport report) {
        authDiscovery.show(report);
        rebuildTabs();
    }

    public void useServerCards(ServerCardSource source) {
        serverCards.useSource(source);
    }

    public void populate(ServerMetadata metadata) {
        showIdentity(ServerIdentityView.of(metadata.serverInfo(),
                ServerIcons.of(metadata.raw(), json(metadata.icons()))));
        capabilities.show(CapabilityCards.grid(json(metadata.capabilities())));
        instructions.show(instructionsText(metadata.instructions()));
        raw.show(metadata.raw());
        advertisedVersions = AdvertisedVersions.of(metadata.raw());
        showSession();
        hasRaw = hasContent(metadata.raw());
        rebuildTabs();
    }

    DetailTabs tabs() {
        return tabs;
    }

    private void showSession() {
        session.show(connection == null ? Placeholders.wrapping(EMPTY_CONNECTION) : sessionFields(connection));
    }

    private FieldGrid sessionFields(ConnectionSummary summary) {
        return new FieldGrid()
                .addComponent("MCP version", ProtocolVersionView.of(summary.protocolVersion()))
                .addMonospaced("Supported versions", advertisedVersions)
                .addText("Requested mode", String.valueOf(summary.requestedTransport()))
                .addText("Connected via", String.valueOf(summary.resolvedTransport()))
                .addMonospaced("Endpoint", summary.displayEndpoint())
                .addMonospaced("Session ID", Objects.requireNonNullElse(summary.sessionId(), NO_SESSION));
    }

    private void showIdentity(ServerIdentityView view) {
        identity.removeAll();
        identity.add(view, BorderLayout.CENTER);
        identity.revalidate();
        identity.repaint();
    }

    private void rebuildTabs() {
        if (hasRaw) {
            tabs.rebuild(this::addContentTabs, raw);
        } else {
            tabs.rebuildWithoutRaw(this::addContentTabs);
        }
    }

    private void addContentTabs(DetailTabs content) {
        content.add(OVERVIEW, overview);
        content.addWhen(serverCards.isConnected(), SERVER_CARD, serverCards);
        content.addWhen(authDiscovery.hasContent(), AUTH_DISCOVERY, authDiscovery);
    }

    private Box overviewStack() {
        Box stack = Box.createVerticalBox();
        stack.setBorder(BorderFactory.createEmptyBorder(PADDING, PADDING, PADDING, PADDING));
        for (JComponent part : List.of(identity, session, authentication, capabilities, instructions)) {
            stack.add(part);
            stack.add(Box.createVerticalStrut(SECTION_GAP));
        }
        return stack;
    }

    private static JPanel topAligned(JComponent content) {
        JPanel holder = new JPanel(new BorderLayout());
        holder.add(content, BorderLayout.NORTH);
        return holder;
    }

    private static JComponent instructionsText(String text) {
        return text == null || text.isBlank() ? null : new WrappingText(text);
    }

    private static boolean hasContent(JsonNode node) {
        return node != null && !node.isEmpty();
    }

    private static JsonNode json(Object value) {
        return McpObjectMapper.RECEIVED_ORDER.valueToTree(value);
    }
}
