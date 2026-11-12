package com.mcpscanner.ui.serverinfo;

import com.mcpscanner.auth.oauth.SignInEvidence;
import com.mcpscanner.auth.oauth.discovery.DiscoveredMetadata;
import com.mcpscanner.auth.oauth.discovery.DiscoverySource;
import com.mcpscanner.auth.oauth.discovery.MetadataFetchFailures;
import com.mcpscanner.auth.oauth.discovery.ProtectedResourceDocument;
import com.mcpscanner.testutil.ShownText;
import com.mcpscanner.testutil.SwingTree;
import com.mcpscanner.ui.detail.RawJsonView;
import com.mcpscanner.ui.widgets.RevealToggle;
import com.nimbusds.oauth2.sdk.as.AuthorizationServerMetadata;
import org.junit.jupiter.api.Test;

import javax.swing.JTabbedPane;
import java.net.URI;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AuthDiscoveryViewTest {

    private static final URI ISSUER = URI.create("https://auth.example");
    private static final URI PRM_URL = URI.create("https://mcp.example/.well-known/oauth-protected-resource");
    private static final String PRM_JSON = "{\"resource\":\"https://mcp.example/mcp\","
            + "\"authorization_servers\":[\"https://auth.example\"]}";
    private static final String AS_JSON = "{\"issuer\":\"https://auth.example\","
            + "\"authorization_endpoint\":\"https://auth.example/authorize\","
            + "\"token_endpoint\":\"https://auth.example/token\",\"response_types_supported\":[\"code\"]}";
    private static final String CHALLENGE = "Bearer resource_metadata=\"" + PRM_URL + "\"";
    private static final String REGISTRATION = "{\"client_id\":\"c-1\",\"client_secret\":\"s3cret\","
            + "\"registration_access_token\":\"rat-9\",\"registration_client_uri\":\"https://auth.example/reg/c-1\"}";

    private final AuthDiscoveryView view = new AuthDiscoveryView(ZoneId.of("UTC"));

    @Test
    void showsTheDiscoveryEvidenceAsSent() {
        view.show(new AuthDiscoveryReport(Optional.of(discovered()), Optional.empty(), Optional.empty()));

        assertThat(ShownText.of(view)).contains(
                "Protected resource metadata URL: " + PRM_URL,
                "Authorization server metadata source: " + DiscoverySource.WWW_AUTHENTICATE_HEADER.displayPath(),
                "WWW-Authenticate challenge: " + CHALLENGE);
        assertThat(json("Protected resource metadata").displayedText()).contains("\"authorization_servers\"");
        assertThat(json("Authorization server metadata").displayedText()).contains("https://auth.example/token");
    }

    @Test
    void fetchFailuresAreDistinguishedFromRequestsNeverMade() {
        DiscoveredMetadata viaAsWellKnown = new DiscoveredMetadata(ISSUER, DiscoverySource.AS_WELL_KNOWN,
                asMetadata(), List.of(), null, null,
                new MetadataFetchFailures("status 404 (" + PRM_URL + ")", null));

        view.show(new AuthDiscoveryReport(Optional.of(viaAsWellKnown), Optional.empty(), Optional.empty()));

        assertThat(ShownText.of(view)).contains(
                "Protected resource metadata URL: fetch failed: status 404 (" + PRM_URL + ")",
                "WWW-Authenticate challenge: not fetched");
    }

    @Test
    void registrationSecretsAreMaskedUntilRevealed() {
        view.show(new AuthDiscoveryReport(Optional.empty(),
                Optional.of(new SignInEvidence(AS_JSON, Optional.of(REGISTRATION))), Optional.empty()));
        RawJsonView registration = json("Registration response");

        assertThat(registration.displayedText()).contains("c-1", "https://auth.example/reg/c-1")
                .doesNotContain("s3cret", "rat-9");

        SwingTree.descendants(view).stream().filter(RevealToggle.class::isInstance)
                .map(RevealToggle.class::cast).findFirst().orElseThrow().doClick();

        assertThat(registration.displayedText()).contains("s3cret", "rat-9");
    }

    @Test
    void authorizationServerMetadataFromTheFlowIsLabelledWhenNothingWasDiscovered() {
        view.show(new AuthDiscoveryReport(Optional.empty(),
                Optional.of(new SignInEvidence(AS_JSON, Optional.empty())), Optional.empty()));

        assertThat(ShownText.of(view)).contains(
                "Authorization server metadata source: issuer metadata, fetched during the OAuth flow",
                "Protected resource metadata URL: not fetched",
                "Registration response: no dynamic registration");
        assertThat(jsonTitles()).doesNotContain("Registration response", "Protected resource metadata");
    }

    @Test
    void tokenExpiryIsAnAbsoluteLocalTime() {
        view.show(new AuthDiscoveryReport(Optional.empty(), Optional.of(SignInEvidence.none()),
                Optional.of(Instant.parse("2026-11-11T14:03:22Z"))));

        assertThat(ShownText.of(view)).contains("Token expires: 2026-11-11 14:03:22 UTC");
    }

    private DiscoveredMetadata discovered() {
        return new DiscoveredMetadata(ISSUER, DiscoverySource.WWW_AUTHENTICATE_HEADER, asMetadata(), List.of(),
                new ProtectedResourceDocument(PRM_URL, PRM_JSON), CHALLENGE);
    }

    private static AuthorizationServerMetadata asMetadata() {
        try {
            return AuthorizationServerMetadata.parse(AS_JSON);
        } catch (com.nimbusds.oauth2.sdk.ParseException invalid) {
            throw new IllegalStateException(invalid);
        }
    }

    private RawJsonView json(String title) {
        JTabbedPane documents = documents();
        int index = documents.indexOfTab(title);
        assertThat(index).as("tab " + title).isNotNegative();
        return SwingTree.descendants((java.awt.Container) documents.getComponentAt(index)).stream()
                .filter(RawJsonView.class::isInstance).map(RawJsonView.class::cast).findFirst().orElseThrow();
    }

    private List<String> jsonTitles() {
        JTabbedPane documents = documents();
        return java.util.stream.IntStream.range(0, documents.getTabCount()).mapToObj(documents::getTitleAt).toList();
    }

    private JTabbedPane documents() {
        return SwingTree.descendants(view).stream().filter(JTabbedPane.class::isInstance)
                .map(JTabbedPane.class::cast).findFirst().orElseThrow();
    }
}
