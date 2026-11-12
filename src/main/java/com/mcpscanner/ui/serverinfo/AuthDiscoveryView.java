package com.mcpscanner.ui.serverinfo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.mcpscanner.auth.oauth.discovery.DiscoveredMetadata;
import com.mcpscanner.auth.oauth.discovery.ProtectedResourceDocument;
import com.mcpscanner.mcp.McpObjectMapper;
import com.mcpscanner.ui.detail.DetailTabs;
import com.mcpscanner.ui.detail.RawJsonView;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.time.ZoneId;
import java.util.Optional;

public final class AuthDiscoveryView extends JPanel {

    private static final String PROTECTED_RESOURCE = "Protected resource metadata";
    private static final String AUTHORIZATION_SERVER = "Authorization server metadata";
    private static final String REGISTRATION = "Registration response";
    private static final String REGISTRATION_HINT = "Secrets are hidden until you reveal them.";
    private static final String AS_SENT = "As the server sent it.";
    private static final String AS_PARSED = "The server's metadata document as the OAuth client parsed it.";
    private static final int PADDING = 8;

    private final AuthDiscoveryFields fields;
    private final JPanel summary = new JPanel(new BorderLayout());
    private final DetailTabs documents = new DetailTabs();
    private AuthDiscoveryReport report = AuthDiscoveryReport.none();

    public AuthDiscoveryView(ZoneId zone) {
        super(new BorderLayout());
        fields = new AuthDiscoveryFields(zone);
        summary.setOpaque(false);
        summary.setBorder(BorderFactory.createEmptyBorder(PADDING, PADDING, PADDING, PADDING));
        add(summary, BorderLayout.NORTH);
        add(documents.component(), BorderLayout.CENTER);
    }

    public void show(AuthDiscoveryReport newReport) {
        report = newReport;
        summary.removeAll();
        summary.add(fields.of(newReport), BorderLayout.CENTER);
        documents.rebuildWithoutRaw(this::addDocuments);
        revalidate();
        repaint();
    }

    public boolean hasContent() {
        return !report.isEmpty();
    }

    private void addDocuments(DetailTabs tabs) {
        protectedResourceJson(report).ifPresent(json -> tabs.add(PROTECTED_RESOURCE, plain(AS_SENT, json)));
        report.authorizationServerMetadataJson().ifPresent(json -> tabs.add(AUTHORIZATION_SERVER, plain(AS_PARSED, json)));
        report.registrationResponseJson().ifPresent(json ->
                tabs.add(REGISTRATION, new MaskedJsonView(REGISTRATION_HINT, parsed(json))));
    }

    private static Optional<String> protectedResourceJson(AuthDiscoveryReport report) {
        return report.discovery().map(DiscoveredMetadata::protectedResource).map(ProtectedResourceDocument::json);
    }

    private static RawJsonView plain(String hint, String json) {
        RawJsonView view = new RawJsonView(hint);
        view.show(parsed(json));
        return view;
    }

    private static JsonNode parsed(String json) {
        try {
            JsonNode node = McpObjectMapper.RECEIVED_ORDER.readTree(json);
            return node == null || node.isMissingNode() ? TextNode.valueOf(json) : node;
        } catch (JsonProcessingException notJson) {
            return TextNode.valueOf(json);
        }
    }
}
