package com.mcpscanner.ui;

import com.mcpscanner.auth.OAuthAuthCodeStrategy;
import com.mcpscanner.auth.oauth.OAuthClientHints;
import com.mcpscanner.auth.oauth.discovery.AuthRequirement;
import com.mcpscanner.auth.oauth.discovery.DiscoveredMetadata;
import com.mcpscanner.auth.oauth.discovery.DiscoveryFailedException;
import com.mcpscanner.client.TransportType;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.scan.request.AdvertisedAuthorizationServer;
import com.mcpscanner.ui.state.ConnectionStatus;
import com.nimbusds.oauth2.sdk.as.AuthorizationServerMetadata;

import java.net.URI;

public interface OAuthConnectSupport {

    default void beginSignInAttempt() {
    }

    OAuthClientHints buildOauthHintsSnapshot();

    DiscoveredMetadata discoverOauthMetadataSync(String endpoint) throws DiscoveryFailedException;

    AuthRequirement probeAuthRequirement(String endpoint, TransportType transport);

    void applyDiscoveredMetadata(DiscoveredMetadata metadata);

    OAuthAuthCodeStrategy completeOAuthDance(OAuthClientHints hints, URI mcpResource, McpEventLog eventLog);

    default OAuthAuthCodeStrategy completeOAuthDance(OAuthClientHints hints, URI mcpResource,
                                                     McpEventLog eventLog,
                                                     AuthorizationServerMetadata preDiscovered) {
        return completeOAuthDance(hints, mcpResource, eventLog);
    }

    void publishOauthStrategy(OAuthAuthCodeStrategy strategy);

    void clearOauthStrategy();

    void clearAutoDiscoveredOAuth();

    OAuthAuthCodeStrategy currentOauthStrategy();

    ConnectionStatus buildConnectedStatus(String host, int toolCount);

    AdvertisedAuthorizationServer authorizationServerOfLastDance();
}
