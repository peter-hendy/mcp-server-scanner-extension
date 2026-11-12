package com.mcpscanner.auth;

import com.mcpscanner.auth.oauth.InMemoryCredentialStore;
import com.mcpscanner.core.oauth.ClientCredentialsConfig;
import com.mcpscanner.core.oauth.ClientCredentialsFlow;
import com.mcpscanner.core.oauth.CredentialBinding;
import com.mcpscanner.core.oauth.EnterpriseIdentityProvider;
import com.mcpscanner.core.oauth.EnterpriseManagedConfig;
import com.mcpscanner.core.oauth.EnterpriseManagedFlow;
import com.mcpscanner.core.oauth.OAuthCredential;
import com.mcpscanner.core.oauth.OAuthFlowException;
import com.mcpscanner.core.oauth.TokenExchange;

import java.net.URI;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

public final class GrantBackedAuthStrategy implements AuthStrategy {

    private record Grant(Supplier<OAuthCredential> credential, Runnable authenticate) {}

    private final EndpointCredentialBinding endpointBinding;
    private final CredentialBinding credentialBinding;
    private final InMemoryCredentialStore credentials;
    private final Grant grant;

    private GrantBackedAuthStrategy(URI intendedEndpoint, CredentialBinding credentialBinding,
                                    InMemoryCredentialStore credentials, Grant grant) {
        this.endpointBinding = new EndpointCredentialBinding(intendedEndpoint, credentialBinding);
        this.credentialBinding = credentialBinding;
        this.credentials = credentials;
        this.grant = grant;
    }

    public static GrantBackedAuthStrategy clientCredentials(URI intendedEndpoint, ClientCredentialsConfig config,
                                                            TokenExchange exchange, Clock clock) {
        var credentials = new InMemoryCredentialStore();
        var flow = new ClientCredentialsFlow(exchange, credentials, clock);
        return new GrantBackedAuthStrategy(intendedEndpoint, config.binding(), credentials,
                new Grant(() -> flow.credential(config), () -> flow.authenticate(config)));
    }

    public static GrantBackedAuthStrategy enterpriseManaged(URI intendedEndpoint, EnterpriseManagedConfig config,
                                                            EnterpriseIdentityProvider identityProvider,
                                                            TokenExchange exchange, Clock clock) {
        if (identityProvider == null) {
            throw new OAuthFlowException(OAuthFlowException.Reason.MISSING_CREDENTIAL,
                    "Organization identity provider is not configured");
        }
        var credentials = new InMemoryCredentialStore();
        var flow = new EnterpriseManagedFlow(identityProvider, exchange, credentials, clock);
        return new GrantBackedAuthStrategy(intendedEndpoint, config.binding(), credentials,
                new Grant(() -> flow.credential(config), () -> flow.authenticate(config)));
    }

    @Override
    public void validateEndpoint(URI endpoint) {
        endpointBinding.validate(endpoint);
    }

    @Override
    public Map<String, String> headers() {
        return Map.of(AuthHeaders.AUTHORIZATION_HEADER, AuthHeaders.BEARER_PREFIX + grant.credential().get().accessToken());
    }

    @Override
    public Set<String> contributedHeaderNames() {
        return Set.of(AuthHeaders.AUTHORIZATION_HEADER);
    }

    @Override
    public boolean supportsRefresh() {
        return true;
    }

    @Override
    public boolean refresh() {
        grant.authenticate().run();
        return true;
    }

    @Override
    public Optional<AccessToken> currentToken() {
        return credentials.load(credentialBinding)
                .map(credential -> AccessToken.of(credential.accessToken(), credential.expiresAt()));
    }
}
