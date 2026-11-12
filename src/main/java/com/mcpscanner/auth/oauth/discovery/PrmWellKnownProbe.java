package com.mcpscanner.auth.oauth.discovery;

import burp.api.montoya.http.Http;
import com.mcpscanner.auth.oauth.safety.DefaultSuspiciousDestinationGate;
import com.mcpscanner.auth.oauth.safety.FetchPurpose;
import com.mcpscanner.auth.oauth.safety.SuspiciousDestinationGate;
import com.mcpscanner.logging.McpEventLog;

import java.net.URI;
import java.util.Optional;

public class PrmWellKnownProbe implements MetadataProbe {

    private static final String PURPOSE = "Protected Resource Metadata (.well-known)";
    private final MetadataDocumentFetcher fetcher;
    private final PrmToAsResolver resolver;
    private final McpEventLog eventLog;
    private final DiscoveryTrail trail;

    public PrmWellKnownProbe(Http http,
                             PrmToAsResolver resolver,
                             SuspiciousDestinationGate gate,
                             McpEventLog eventLog) {
        this(http, resolver, gate, eventLog, new DiscoveryTrail());
    }

    PrmWellKnownProbe(Http http,
                      PrmToAsResolver resolver,
                      SuspiciousDestinationGate gate,
                      McpEventLog eventLog,
                      DiscoveryTrail trail) {
        this.trail = trail;
        this.resolver = resolver;
        this.eventLog = eventLog != null ? eventLog : McpEventLog.noop();
        this.fetcher = new MetadataDocumentFetcher(http, gate, this.eventLog);
    }

    @Override
    public Optional<DiscoveredMetadata> probe(URI mcpResource) {
        return OAuthWellKnownPaths.protectedResourceMetadataUrl(mcpResource).flatMap(prmUrl ->
                fetcher.fetchOk(prmUrl, FetchPurpose.of(PURPOSE, mcpResource),
                                reason -> trail.protectedResourceFetchFailed(reason + " (" + prmUrl + ")"))
                        .flatMap(body -> resolver.resolveFetchedPrm(
                                prmUrl, DiscoverySource.PRM_WELL_KNOWN, mcpResource, body)));
    }

}
