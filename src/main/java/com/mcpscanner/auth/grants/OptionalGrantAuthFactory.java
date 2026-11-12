package com.mcpscanner.auth.grants;

import burp.api.montoya.http.Http;
import com.mcpscanner.auth.AuthStrategy;
import com.mcpscanner.auth.GrantBackedAuthStrategy;
import com.mcpscanner.auth.oauth.BurpHttpRequestSender;
import com.mcpscanner.auth.oauth.ExpectedIssuer;
import com.mcpscanner.auth.oauth.TokenEndpointClient;
import com.mcpscanner.auth.oauth.safety.SuspiciousDestinationGate;
import com.mcpscanner.core.oauth.OAuthFlowException;

import java.net.URI;
import java.time.Clock;
import java.util.Map;
import java.util.Objects;

public final class OptionalGrantAuthFactory {
    private final GuardedGrantMetadataDiscovery discovery;
    private final Map<OptionalGrantSettings.Profile, GrantProfile> profiles;

    public OptionalGrantAuthFactory(Http http, SuspiciousDestinationGate gate,
                                    EnterpriseIdentityProviderRegistry providers, Clock clock) {
        this(new BurpHttpRequestSender(http), gate, providers, clock);
    }

    public OptionalGrantAuthFactory(BurpHttpRequestSender sender, SuspiciousDestinationGate gate,
                                    EnterpriseIdentityProviderRegistry providers, Clock clock) {
        var exchange = new TokenEndpointClient(sender, gate);
        Objects.requireNonNull(clock);
        this.discovery = new GuardedGrantMetadataDiscovery(sender, gate);
        this.profiles = Map.of(
                OptionalGrantSettings.Profile.CLIENT_CREDENTIALS, new ClientCredentialsGrantProfile(exchange, clock),
                OptionalGrantSettings.Profile.ENTERPRISE_MANAGED,
                new EnterpriseManagedGrantProfile(Objects.requireNonNull(providers), exchange, clock));
    }

    public AuthStrategy create(URI endpoint, OptionalGrantSettings settings) {
        var boundGrant = profiles.get(Objects.requireNonNull(settings).profile()).bind(settings);
        var metadata = discoverIssuedBy(endpoint, settings.expectedIssuer());
        GrantBackedAuthStrategy strategy = boundGrant.strategyFor(endpoint, metadata);
        strategy.refresh();
        return strategy;
    }

    private GuardedGrantMetadataDiscovery.Metadata discoverIssuedBy(URI endpoint, URI expectedIssuer) {
        var metadata = discovery.discover(endpoint, expectedIssuer);
        if (!new ExpectedIssuer(expectedIssuer).matches(metadata.issuer())) {
            throw new OAuthFlowException(OAuthFlowException.Reason.INVALID_CONFIGURATION,
                    "Discovered authorization server does not match the configured issuer");
        }
        return metadata;
    }
}
