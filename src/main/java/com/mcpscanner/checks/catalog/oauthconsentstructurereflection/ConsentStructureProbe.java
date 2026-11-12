package com.mcpscanner.checks.catalog.oauthconsentstructurereflection;

import burp.api.montoya.http.message.HttpRequestResponse;
import com.mcpscanner.checks.api.SkipReason;
import com.mcpscanner.checks.shared.oauth.AuthorizationServer;
import com.mcpscanner.checks.shared.oauth.AuthorizationServerDiscovery;
import com.mcpscanner.checks.shared.oauth.OAuthHttp;
import com.mcpscanner.checks.shared.oauth.ProbeClientRegistrar;
import com.mcpscanner.checks.shared.oauth.RegisteredClient;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

final class ConsentStructureProbe {

    static final String CONTROL_CLIENT_SUFFIX = ".mcpscan-unregistered-control";

    private final OAuthHttp http;
    private final AuthorizationServerDiscovery discovery;
    private final AuthorizationPageFetcher fetcher;
    private final StructureMarkers markers;
    private final DecisionLog decisions;

    ConsentStructureProbe(OAuthHttp http, StructureMarkers markers, DecisionLog decisions) {
        this.http = http;
        this.discovery = new AuthorizationServerDiscovery(http);
        this.fetcher = new AuthorizationPageFetcher(http);
        this.markers = markers;
        this.decisions = decisions;
    }

    Optional<ConsentStructureEvidence> run(HttpRequestResponse base) {
        String mcpEndpoint = base.request().url();
        try {
            return probe(base, mcpEndpoint);
        } finally {
            decisions.stoppedIfRateLimited(mcpEndpoint);
        }
    }

    private Optional<ConsentStructureEvidence> probe(HttpRequestResponse base, String mcpEndpoint) {
        Optional<AuthorizationServer> server = discovery.discover(base);
        Optional<URI> registrationEndpoint = server.flatMap(AuthorizationServer::registrationEndpoint);
        if (registrationEndpoint.isEmpty()) {
            return decisions.notRun(mcpEndpoint, SkipReason.prerequisiteMissing(
                    "no OAuth authorization server with a registration endpoint was discovered"));
        }
        ProbeClientRegistrar registrar = new ProbeClientRegistrar(http, registrationEndpoint.get());
        try {
            return register(server.get(), registrar, mcpEndpoint);
        } finally {
            decisions.cleanedUp(registrar.deleteRegisteredClients());
        }
    }

    private Optional<ConsentStructureEvidence> register(AuthorizationServer server, ProbeClientRegistrar registrar,
                                                        String mcpEndpoint) {
        FieldMarkers fieldMarkers = FieldMarkers.from(markers);
        Optional<RegisteredClient> client = registrar
                .register(ConsentClientRegistration.carrying(fieldMarkers.clientName())).client();
        if (client.isEmpty()) {
            return decisions.notRun(mcpEndpoint,
                    SkipReason.prerequisiteMissing("dynamic client registration did not issue a client_id"));
        }
        AuthorizationRequest request = new AuthorizationRequest(server, client.get().clientId(),
                ConsentClientRegistration.REDIRECT_URI, fieldMarkers.state().payload());
        return inspect(client.get(), request, fieldMarkers, mcpEndpoint);
    }

    private Optional<ConsentStructureEvidence> inspect(RegisteredClient client, AuthorizationRequest request,
                                                       FieldMarkers fieldMarkers, String mcpEndpoint) {
        Optional<AuthorizationPage> page = fetcher.fetch(request.uri());
        if (page.isEmpty()) {
            return decisions.notRun(mcpEndpoint,
                    SkipReason.transportFailure("the authorization request received no response"));
        }
        List<FieldReflection> reflections = fieldMarkers.reflectionsIn(page.get().document());
        if (reflections.isEmpty()) {
            return decisions.notReported("no marker parsed as HTML structure on the authorization page");
        }
        Optional<AuthorizationPage> control = fetcher.fetch(request.forClient(client.clientId() + CONTROL_CLIENT_SUFFIX).uri());
        if (control.isEmpty()) {
            return decisions.notRun(mcpEndpoint,
                    SkipReason.transportFailure("the unregistered-client control received no response"));
        }
        List<FieldReflection> retained = absentFrom(reflections, fieldMarkers, control.get());
        if (retained.isEmpty()) {
            return decisions.notReported("the unregistered-client control rendered the same structure");
        }
        return Optional.of(new ConsentStructureEvidence(client, page.get(), control.get(), retained,
                ScriptExecutionAssessment.of(page.get().enforcedContentSecurityPolicies())));
    }

    private static List<FieldReflection> absentFrom(List<FieldReflection> reflections, FieldMarkers fieldMarkers,
                                                    AuthorizationPage control) {
        Set<ReflectedField> echoed = fieldMarkers.reflectionsIn(control.document()).stream()
                .map(FieldReflection::field)
                .collect(Collectors.toSet());
        return reflections.stream().filter(reflection -> !echoed.contains(reflection.field())).toList();
    }
}
