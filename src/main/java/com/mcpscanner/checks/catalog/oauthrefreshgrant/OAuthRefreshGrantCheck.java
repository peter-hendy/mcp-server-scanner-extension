package com.mcpscanner.checks.catalog.oauthrefreshgrant;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.scanner.audit.issues.AuditIssue;
import com.mcpscanner.auth.AuthStrategy;
import com.mcpscanner.auth.grant.RefreshGrantStore;
import com.mcpscanner.checks.api.CheckDescriptor;
import com.mcpscanner.checks.api.CheckLog;
import com.mcpscanner.checks.api.HostCheck;
import com.mcpscanner.checks.api.OAuthSkipLog;
import com.mcpscanner.checks.api.SkipReason;
import com.mcpscanner.checks.shared.oauth.OAuthHttp;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.mcp.McpRequestDetector;
import com.mcpscanner.mcp.McpRequestKind;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public final class OAuthRefreshGrantCheck implements HostCheck {

    private final Supplier<AuthStrategy> authStrategy;
    private final CheckLog log;

    public OAuthRefreshGrantCheck(Supplier<AuthStrategy> authStrategy, McpEventLog eventLog) {
        this.authStrategy = authStrategy;
        this.log = new CheckLog(eventLog, OAuthRefreshGrantDescriptor.DESCRIPTOR.id());
    }

    @Override
    public CheckDescriptor descriptor() {
        return OAuthRefreshGrantDescriptor.DESCRIPTOR;
    }

    @Override
    public boolean appliesTo(HttpRequestResponse hostBaseRequestResponse) {
        return McpRequestDetector.classify(hostBaseRequestResponse) != McpRequestKind.NOT_MCP
                && authStrategy.get().refreshGrantStore().isPresent();
    }

    @Override
    public String appliesOnlyTo() {
        return "MCP requests while the OAuth session holds a refresh token";
    }

    @Override
    public List<AuditIssue> audit(HttpRequestResponse hostBaseRequestResponse, Http http) {
        String mcpEndpoint = hostBaseRequestResponse.request().url();
        Optional<RefreshGrantStore> store = authStrategy.get().refreshGrantStore();
        if (store.isEmpty()) {
            log.notRun(mcpEndpoint, SkipReason.prerequisiteMissing("the OAuth session holds no refresh token"));
            return List.of();
        }
        Optional<URI> issuer = store.get().withExclusiveGrant(live -> live.grant().issuer());
        if (issuer.isEmpty()) {
            log.notRun(mcpEndpoint, RefreshGrantAudit.NO_LIVE_GRANT);
            return List.of();
        }
        OAuthHttp oauthHttp = new OAuthHttp(http);
        Optional<AuthorizationServerMetadata> metadata =
                new AuthorizationServerMetadataLookup(oauthHttp).lookup(issuer.get());
        if (metadata.isEmpty()) {
            new OAuthSkipLog(log, oauthHttp).notRun(mcpEndpoint, SkipReason.prerequisiteMissing(
                    "no authorization server metadata with a token endpoint for " + issuer.get()));
            return List.of();
        }
        return new RefreshGrantAudit(http, hostBaseRequestResponse, authStrategy, metadata.get(), log)
                .run(store.get());
    }
}
