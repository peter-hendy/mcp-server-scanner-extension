package com.mcpscanner.core.oauth;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class OAuthPolicyTest {
    public static void main(String[] args) {
        discoversChallengeMetadataAndScopes();
        acceptsUnquotedHttpsResourceMetadata();
        preservesQuotedMetadataAndRejectsAmbiguousUriParameters();
        combinesRepeatedChallengeFields();
        ignoresEmptyChallengeListMembers();
        validatesDiscoveryBindings();
        acceptsExplicitHttpsDefaultResourcePort();
        bindsRootFallbackToEndpointOrOrigin();
        acceptsAdvertisedAncestorResource();
        acceptsQueryEndpointWithBoundAudience();
        rejectsUnrelatedOrAmbiguousResources();
        requestsApprovedScopeUpgrade();
        boundsRecoveryAndRejectsMismatches();
        rejectsAmbiguousChallenges();
        rejectsMalformedCombinedFields();
        acceptsRepeatedNonConflictingBearerChallenges();
        narrowerGrantAfterStepUpDoesNotRepromptApprovedScopes();
        treatsOnlyLoopbackHostsAsHttpSafe();
        discoversLocalhostProtectedResources();
        remoteResourceCannotAdvertiseLoopbackMetadata();
        System.out.println("OAuthPolicyTest passed");
    }

    private static void acceptsExplicitHttpsDefaultResourcePort() {
        var policy = new OAuthDiscoveryPolicy();
        for (String endpoint : List.of("https://mcp.example/mcp", "https://mcp.example:443/mcp")) {
            String advertised = endpoint.contains(":443") ? "https://mcp.example/mcp" : "https://mcp.example:443/mcp";
            var document = Map.<String, Object>of("resource", advertised, "authorization_servers", List.of("https://issuer.example"));
            require(policy.validateResourceMetadata(URI.create(endpoint), document).resource().equals(URI.create(advertised)), "Preserve advertised canonical audience");
            var candidate = policy.protectedResourceCandidates(URI.create(endpoint), java.util.Optional.empty()).get(0);
            require(policy.validateResourceMetadata(candidate, document).resource().equals(URI.create(advertised)), "Candidate accepts HTTPS default port");
        }
        expect(OAuthPolicyException.Reason.RESOURCE_MISMATCH, () -> policy.validateResourceMetadata(URI.create("https://mcp.example:8443/mcp"),
                Map.of("resource", "https://mcp.example/mcp", "authorization_servers", List.of("https://issuer.example"))));
        expect(OAuthPolicyException.Reason.ISSUER_MISMATCH, () -> policy.validateIssuerMetadata(URI.create("https://issuer.example:443"), Map.of("issuer", "https://issuer.example")));
    }

    private static void discoversChallengeMetadataAndScopes() {
        var challenge = new OAuthChallengeParser().parse(List.of("Basic realm=\"legacy\", Bearer realm=\"mcp, server\", resource_metadata=\"https://mcp.example/metadata\", scope=\"tools:read files:read\""));
        require(challenge.isPresent(), "Bearer challenge parsed after another scheme");
        var policy = new OAuthDiscoveryPolicy();
        require(policy.protectedResourceLocations(URI.create("https://mcp.example/mcp"), challenge).equals(List.of(URI.create("https://mcp.example/metadata"))), "Challenge metadata takes precedence");
        require(policy.initialScopes(challenge, Set.of("other")).equals(Set.of("tools:read", "files:read")), "Challenge scope is authoritative");
    }

    private static void combinesRepeatedChallengeFields() {
        var challenge = new OAuthChallengeParser().parse(List.of("Bearer scope=read", "resource_metadata=\"https://mcp.example/meta\"")).orElseThrow();
        require(challenge.scopes().equals(Set.of("read")), "Scopes retained across repeated fields");
        require(challenge.resourceMetadata().equals(URI.create("https://mcp.example/meta")), "Parameters continue across repeated fields");
    }

    private static void acceptsUnquotedHttpsResourceMetadata() {
        var parser = new OAuthChallengeParser();
        for (String location : List.of("https://mcp.example/.well-known/oauth-protected-resource",
                "https://metadata.example:8443/tenant/resource?version=2&client=mcp")) {
            var challenge = parser.parse(List.of("Bearer resource_metadata=" + location + ", scope=read")).orElseThrow();
            require(challenge.resourceMetadata().equals(URI.create(location)), "Unquoted HTTPS metadata retains exact location");
            require(challenge.scopes().equals(Set.of("read")), "Unquoted metadata preserves following parameters");
        }
    }

    private static void preservesQuotedMetadataAndRejectsAmbiguousUriParameters() {
        var parser = new OAuthChallengeParser();
        String location = "https://metadata.example/tenant/meta?version=2";
        for (String value : List.of(location, "\"" + location + "\"")) {
            var challenge = parser.parse(List.of("Basic realm=\"legacy\", Bearer scope=read, resource_metadata=" + value)).orElseThrow();
            require(challenge.resourceMetadata().equals(URI.create(location)), "Quoted and unquoted metadata behave identically");
            require(challenge.scopes().equals(Set.of("read")), "Metadata tolerance preserves combined challenges");
        }
        for (String header : List.of(
                "Bearer resource_metadata=http://metadata.example/meta",
                "Bearer resource_metadata=http://127.0.0.1/meta",
                "Bearer resource_metadata=https://user:server-secret@metadata.example/meta",
                "Bearer resource_metadata=https://metadata.example/meta#fragment",
                "Bearer resource_metadata=https://metadata.example/meta scope=read",
                "Bearer resource_metadata=https://metadata.example/meta\t scope=read",
                "Bearer resource_metadata=https://metadata.example/meta\r\n",
                "Bearer resource_metadata=https://metadata.example/meta\"suffix\"",
                "Bearer resource_metadata=" + location + ", resource_metadata=" + location,
                "Bearer scope=https://metadata.example/meta",
                "Bearer error=https://metadata.example/meta")) {
            expect(OAuthPolicyException.Reason.INVALID_CHALLENGE, () -> parser.parse(List.of(header)));
        }
    }

    private static void ignoresEmptyChallengeListMembers() {
        var challenge = new OAuthChallengeParser().parse(List.of("Bearer scope=read, , resource_metadata=\"https://mcp.example/meta\"")).orElseThrow();
        require(challenge.scopes().equals(Set.of("read")), "Empty members preserve challenge scope");
        require(challenge.resourceMetadata().equals(URI.create("https://mcp.example/meta")), "Empty members preserve challenge parameters");
    }

    private static void validatesDiscoveryBindings() {
        var policy = new OAuthDiscoveryPolicy();
        URI resource = URI.create("https://mcp.example/public/mcp");
        URI issuer = URI.create("https://issuer.example/tenant");
        require(policy.protectedResourceLocations(resource, java.util.Optional.empty()).equals(List.of(
                URI.create("https://mcp.example/.well-known/oauth-protected-resource/public/mcp"),
                URI.create("https://mcp.example/.well-known/oauth-protected-resource"))), "Resource fallback candidates ordered");
        require(policy.authorizationServerLocations(issuer).equals(List.of(
                URI.create("https://issuer.example/.well-known/oauth-authorization-server/tenant"),
                URI.create("https://issuer.example/.well-known/openid-configuration/tenant"),
                URI.create("https://issuer.example/tenant/.well-known/openid-configuration"))), "RFC8414 and OIDC candidates ordered");
        var metadata = policy.validateResourceMetadata(resource, Map.of("resource", resource.toString(),
                "authorization_servers", List.of(issuer.toString()), "scopes_supported", List.of("read")));
        require(metadata.issuers().equals(List.of(issuer)), "Issuer selected only from validated resource metadata");
        require(policy.initialScopes(java.util.Optional.empty(), metadata.scopes()).equals(Set.of("read")), "Metadata initial scopes used without challenge");
        require(policy.initialScopes(java.util.Optional.empty(), Set.of()).isEmpty(), "Missing scopes omits scope parameter");
        policy.validateIssuerMetadata(issuer, Map.of("issuer", issuer.toString()));
        expect(OAuthPolicyException.Reason.RESOURCE_MISMATCH, () -> policy.validateResourceMetadata(resource,
                Map.of("resource", "https://other.example/mcp", "authorization_servers", List.of(issuer.toString()))));
        expect(OAuthPolicyException.Reason.ISSUER_MISMATCH, () -> policy.validateIssuerMetadata(issuer,
                Map.of("issuer", "https://issuer.example/tenant/")));
        expect(OAuthPolicyException.Reason.INVALID_METADATA, () -> policy.validateResourceMetadata(resource,
                Map.of("resource", resource.toString(), "authorization_servers", List.of())));
    }

    private static void bindsRootFallbackToEndpointOrOrigin() {
        var policy = new OAuthDiscoveryPolicy();
        URI endpoint = URI.create("https://mcp.example/public/mcp");
        URI origin = URI.create("https://mcp.example");
        var candidates = policy.protectedResourceCandidates(endpoint, java.util.Optional.empty());
        var document = Map.<String, Object>of("resource", origin.toString(), "authorization_servers", List.of("https://issuer.example"));
        require(policy.validateResourceMetadata(candidates.get(1), document).resource().equals(origin), "Root fallback retains origin audience");
        require(policy.validateResourceMetadata(candidates.get(0), document).resource().equals(origin), "Path metadata retains origin audience");
        for (String resource : List.of("https://other.example", "https://mcp.example/other")) {
            expect(OAuthPolicyException.Reason.RESOURCE_MISMATCH, () -> policy.validateResourceMetadata(candidates.get(1),
                    Map.of("resource", resource, "authorization_servers", List.of("https://issuer.example"))));
        }
        var challenge = new OAuthChallengeParser().parse(List.of("Bearer resource_metadata=\"https://mcp.example/.well-known/oauth-protected-resource\""));
        var explicit = policy.protectedResourceCandidates(endpoint, challenge);
        require(explicit.size() == 1, "Challenge suppresses derived fallback");
        require(policy.validateResourceMetadata(explicit.get(0), document).resource().equals(origin), "Challenge metadata retains origin audience");
        require(policy.validateResourceMetadata(candidates.get(1), Map.of("resource", endpoint.toString(),
                "authorization_servers", List.of("https://issuer.example"))).resource().equals(endpoint), "Root metadata may bind exact endpoint");
    }

    private static void acceptsAdvertisedAncestorResource() {
        var policy = new OAuthDiscoveryPolicy();
        URI endpoint = URI.create("https://mcp.example/tenant/mcp");
        var challenge = new OAuthChallengeParser().parse(List.of("Bearer resource_metadata=\"https://mcp.example/metadata\""));
        var candidate = policy.protectedResourceCandidates(endpoint, challenge).get(0);
        var document = Map.<String, Object>of("resource", "https://mcp.example/tenant", "authorization_servers", List.of("https://issuer.example"));
        require(policy.validateResourceMetadata(candidate, document).resource().equals(URI.create("https://mcp.example/tenant")),
                "Explicit challenge metadata retains ancestor audience");
        for (String resource : List.of("https://mcp.example/", "https://mcp.example:443/tenant/")) {
            var metadata = policy.validateResourceMetadata(candidate,
                    Map.of("resource", resource, "authorization_servers", List.of("https://issuer.example")));
            require(metadata.resource().toString().equals(resource), "Advertised root and trailing-slash ancestor are preserved");
        }
    }

    private static void rejectsUnrelatedOrAmbiguousResources() {
        var policy = new OAuthDiscoveryPolicy();
        URI endpoint = URI.create("https://mcp.example/tenant/mcp");
        for (String resource : List.of("https://other.example/tenant", "https://mcp.example:8443/tenant",
                "https://mcp.example/ten", "https://mcp.example/tenant-other", "https://mcp.example/sibling",
                "https://mcp.example/tenant/mcp/child", "https://mcp.example/tenant?version=1",
                "https://mcp.example/tenant#fragment", "https://mcp.example/tenant/../tenant",
                "https://mcp.example/tenant/%2e%2e", "https://mcp.example/tenant%2fmcp",
                "https://mcp.example/tenant%5cmcp", "https://mcp.example/tenant/%252e%252e",
                "https://mcp.example//tenant")) {
            expect(OAuthPolicyException.Reason.RESOURCE_MISMATCH, () -> policy.validateResourceMetadata(endpoint,
                    Map.of("resource", resource, "authorization_servers", List.of("https://issuer.example"))));
        }
        for (String target : List.of("https://mcp.example/tenant/../mcp", "https://mcp.example/tenant/%2e%2e/mcp",
                "https://mcp.example/tenant%2fmcp", "https://mcp.example/tenant%5cmcp",
                "https://mcp.example/tenant/%252e%252e/mcp", "https://mcp.example//mcp",
                "https://mcp.example/mcp#fragment")) {
            OAuthPolicyException.Reason reason = target.contains("#") ? OAuthPolicyException.Reason.INVALID_METADATA
                    : OAuthPolicyException.Reason.RESOURCE_MISMATCH;
            expect(reason, () -> policy.validateResourceMetadata(URI.create(target),
                    Map.of("resource", "https://mcp.example", "authorization_servers", List.of("https://issuer.example"))));
            if (!target.contains("#")) {
                for (var candidate : policy.protectedResourceCandidates(URI.create(target), java.util.Optional.empty())) {
                    expect(reason, () -> policy.validateResourceMetadata(candidate,
                            Map.of("resource", "https://mcp.example", "authorization_servers", List.of("https://issuer.example"))));
                }
            }
        }
    }

    private static void acceptsQueryEndpointWithBoundAudience() {
        var policy = new OAuthDiscoveryPolicy();
        URI endpoint = URI.create("https://mcp.example/tenant/mcp?client=one");
        for (String resource : List.of("https://mcp.example", "https://mcp.example/tenant",
                "https://mcp.example/tenant/mcp?client=one")) {
            var metadata = policy.validateResourceMetadata(endpoint,
                    Map.of("resource", resource, "authorization_servers", List.of("https://issuer.example")));
            require(metadata.resource().toString().equals(resource), "Query endpoint retains bound audience");
        }
        for (String resource : List.of("https://mcp.example/tenant?client=one",
                "https://mcp.example/tenant/mcp?client=two",
                "https://mcp.example/tenant/mcp?")) {
            expect(OAuthPolicyException.Reason.RESOURCE_MISMATCH, () -> policy.validateResourceMetadata(endpoint,
                    Map.of("resource", resource, "authorization_servers", List.of("https://issuer.example"))));
        }
    }

    private static void requestsApprovedScopeUpgrade() {
        var binding = new CredentialBinding(URI.create("https://issuer.example"), URI.create("https://mcp.example/mcp"), "client", Set.of("read"));
        var state = OAuthRetryState.start(binding, "tools/call:save");
        var challenge = new OAuthChallengeParser().parse(List.of("Bearer error=\"insufficient_scope\", scope=\"write\", error_description=\"server-secret\""));
        var policy = new OAuthChallengePolicy();
        var pending = policy.decide(403, challenge, binding, state, true, true, AuthorizationApproval.REQUIRE_PROMPT);
        require(pending.action() == OAuthChallengeDecision.Action.APPROVAL_REQUIRED, "Scope upgrades require approval");
        require(pending.scopes().equals(Set.of("read", "write")), "Previously requested scopes preserved");
        var denied = policy.decide(403, challenge, binding, state, true, true, AuthorizationApproval.DENIED);
        require(denied.action() == OAuthChallengeDecision.Action.DENIED, "Approval denial never authorizes");
        var approved = policy.decide(403, challenge, binding, state, true, true, AuthorizationApproval.APPROVED);
        require(approved.action() == OAuthChallengeDecision.Action.AUTHORIZE && approved.nextState().attempts() == 1, "Approved step-up consumes one retry");
        require(policy.decide(403, challenge, binding, approved.nextState(), true, true, AuthorizationApproval.APPROVED).action()
                == OAuthChallengeDecision.Action.PERMANENT_FAILURE, "Same failing scope upgrade never loops");
        require(!approved.toString().contains("server-secret"), "Decision never includes server description");
    }

    private static void narrowerGrantAfterStepUpDoesNotRepromptApprovedScopes() {
        var approvedBinding = new CredentialBinding(URI.create("https://issuer.example"), URI.create("https://mcp.example/mcp"), "client", Set.of("read", "write"));
        var nextOperation = OAuthRetryState.start(approvedBinding, "tools/call:save");
        var challenge = new OAuthChallengeParser().parse(List.of("Bearer error=insufficient_scope, scope=write"));
        require(new OAuthChallengePolicy().decide(403, challenge, approvedBinding, nextOperation, true, true, AuthorizationApproval.APPROVED).action()
                == OAuthChallengeDecision.Action.PERMANENT_FAILURE, "Already approved scope withheld by the AS is not requested again");
    }

    private static void treatsOnlyLoopbackHostsAsHttpSafe() {
        for (String location : List.of("http://localhost:3000/mcp", "http://LOCALHOST/mcp",
                "http://127.0.0.1/mcp", "http://127.255.0.9:8080/mcp", "http://[::1]:3000/mcp", "https://mcp.example/mcp")) {
            require(OAuthDiscoveryPolicy.validLocation(URI.create(location)), "Loopback or HTTPS location accepted: " + location);
        }
        for (String location : List.of("http://mcp.example/mcp", "http://localhost.example/mcp", "http://notlocalhost/mcp",
                "http://128.0.0.1/mcp", "http://127.0.0.256/mcp", "http://127.0.0/mcp", "http://127.example/mcp",
                "http://10.0.0.1/mcp", "http://169.254.169.254/latest", "http://[::2]/mcp", "http://app.localhost/mcp")) {
            require(!OAuthDiscoveryPolicy.validLocation(URI.create(location)), "Non-loopback HTTP location rejected: " + location);
        }
    }

    private static void remoteResourceCannotAdvertiseLoopbackMetadata() {
        var policy = new OAuthDiscoveryPolicy();
        for (String advertised : List.of("http://127.0.0.1:1337/v0.1/", "https://localhost:8443/meta")) {
            var challenge = new OAuthChallengeParser().parse(List.of("Bearer resource_metadata=\"" + advertised + "\""));
            expect(OAuthPolicyException.Reason.INVALID_METADATA,
                    () -> policy.protectedResourceCandidates(URI.create("https://mcp.example/mcp"), challenge));
        }
    }

    private static void discoversLocalhostProtectedResources() {
        URI endpoint = URI.create("http://localhost:3000/mcp");
        var challenge = new OAuthChallengeParser().parse(List.of("Bearer resource_metadata=\"http://localhost:3000/.well-known/oauth-protected-resource/mcp\""));
        var candidates = new OAuthDiscoveryPolicy().protectedResourceCandidates(endpoint, challenge);
        require(candidates.get(0).location().toString().equals("http://localhost:3000/.well-known/oauth-protected-resource/mcp"), "Localhost challenge metadata followed");
        var metadata = new OAuthDiscoveryPolicy().validateResourceMetadata(candidates.get(0),
                Map.of("resource", "http://localhost:3000/mcp", "authorization_servers", List.of("http://localhost:3000")));
        require(metadata.issuers().equals(List.of(URI.create("http://localhost:3000"))), "Localhost authorization server accepted");
    }

    private static void boundsRecoveryAndRejectsMismatches() {
        var binding = new CredentialBinding(URI.create("https://issuer.example"), URI.create("https://mcp.example/mcp"), "client", Set.of("read"));
        var state = OAuthRetryState.start(binding, "tools/call");
        var policy = new OAuthChallengePolicy();
        var empty = java.util.Optional.<BearerChallenge>empty();
        var refresh = policy.decide(401, empty, binding, state, true, true, AuthorizationApproval.REQUIRE_PROMPT);
        require(refresh.action() == OAuthChallengeDecision.Action.REFRESH, "401 refreshes a live refreshable credential once");
        var prompt = policy.decide(401, empty, binding, refresh.nextState(), true, true, AuthorizationApproval.REQUIRE_PROMPT);
        require(prompt.action() == OAuthChallengeDecision.Action.APPROVAL_REQUIRED, "Second 401 requires approval for authorization");
        var authorize = policy.decide(401, empty, binding, refresh.nextState(), true, true, AuthorizationApproval.APPROVED);
        require(authorize.action() == OAuthChallengeDecision.Action.AUTHORIZE, "Approved reauthorization follows failed refresh");
        require(policy.decide(401, empty, binding, authorize.nextState(), true, true, AuthorizationApproval.APPROVED).action()
                == OAuthChallengeDecision.Action.PERMANENT_FAILURE, "401 retry budget is bounded");
        require(policy.decide(401, empty, binding, state, false, false, AuthorizationApproval.REQUIRE_PROMPT).action()
                == OAuthChallengeDecision.Action.APPROVAL_REQUIRED, "Initial authentication prompts for authorization");
        require(policy.decide(403, empty, binding, state, true, true, AuthorizationApproval.APPROVED).action()
                == OAuthChallengeDecision.Action.NONE, "Generic 403 never triggers scope escalation");
        var unrelated = new OAuthChallengeParser().parse(List.of("Bearer error=\"invalid_token\", scope=\"write\""));
        require(policy.decide(403, unrelated, binding, state, true, true, AuthorizationApproval.APPROVED).action()
                == OAuthChallengeDecision.Action.NONE, "Only insufficient_scope starts step-up");
        var wrongResource = new CredentialBinding(binding.issuer(), URI.create("https://other.example/mcp"), "client", binding.scopes());
        expect(OAuthPolicyException.Reason.RESOURCE_MISMATCH, () -> policy.decide(401, empty, wrongResource, state, true, true, AuthorizationApproval.APPROVED));
        var wrongIssuer = new CredentialBinding(URI.create("https://other.example"), binding.resource(), "client", binding.scopes());
        expect(OAuthPolicyException.Reason.ISSUER_MISMATCH, () -> policy.decide(401, empty, wrongIssuer, state, true, true, AuthorizationApproval.APPROVED));
        var first = new OAuthChallengeParser().parse(List.of("Bearer error=insufficient_scope, scope=write"));
        var second = new OAuthChallengeParser().parse(List.of("Bearer error=insufficient_scope, scope=delete"));
        var third = new OAuthChallengeParser().parse(List.of("Bearer error=insufficient_scope, scope=admin"));
        var one = policy.decide(403, first, binding, state, true, true, AuthorizationApproval.APPROVED);
        var two = policy.decide(403, second, binding, one.nextState(), true, true, AuthorizationApproval.APPROVED);
        require(two.scopes().equals(Set.of("read", "write", "delete")), "Scopes accumulate across incremental challenges");
        require(policy.decide(403, third, binding, two.nextState(), true, true, AuthorizationApproval.APPROVED).action()
                == OAuthChallengeDecision.Action.PERMANENT_FAILURE, "New scopes cannot defeat retry budget");
        var denied = policy.decide(403, first, binding, state, true, true, AuthorizationApproval.DENIED);
        require(policy.decide(403, first, binding, denied.nextState(), true, true, AuthorizationApproval.APPROVED).action()
                == OAuthChallengeDecision.Action.PERMANENT_FAILURE, "Denied operation is terminal");
    }

    private static void rejectsAmbiguousChallenges() {
        var parser = new OAuthChallengeParser();
        for (String header : List.of("Bearer scope=\"read  write\"", "Bearer scope=\"read\\\"write\"", "Bearer scope=\"read\\\\write\"",
                "Bearer scope=read, scope=write", "Bearer scope=read, Bearer scope=write", "Bearer scope=\"server-secret", "Bearer resource_metadata=\"https://user:server-secret@mcp.example/meta\"")) {
            expect(OAuthPolicyException.Reason.INVALID_CHALLENGE, () -> parser.parse(List.of(header)));
        }
        expect(OAuthPolicyException.Reason.INVALID_CHALLENGE, () -> new BearerChallenge(null, "insufficient_scope", Set.of("bad scope"), true));
        var mixed = parser.parse(List.of("Digest realm=\"ignored, comma\", qop=\"auth,auth-int\"", "bEaReR scope=read, Basic realm=\"next\""));
        require(mixed.orElseThrow().scopes().equals(Set.of("read")), "Multiple header values and other schemes parsed");
        require(parser.parse(List.of("Basic realm=\"server-secret\"")).isEmpty(), "Unrelated schemes ignored");
    }

    private static void acceptsRepeatedNonConflictingBearerChallenges() {
        var parser = new OAuthChallengeParser();
        var trailingBare = parser.parse(List.of(
            "Bearer resource_metadata=\"https://tenant.erp.example/.well-known/oauth-protected-resource\", Bearer")).orElseThrow();
        require(trailingBare.resourceMetadata().toString().equals("https://tenant.erp.example/.well-known/oauth-protected-resource"),
            "RFC 9110 permits a second, parameterless Bearer challenge");
        var split = parser.parse(List.of("Bearer realm=\"Doorkeeper\", error=\"invalid_token\", "
            + "error_description=\"The access token is invalid\", Bearer resource_metadata=\"https://civic.example/.well-known/oauth-protected-resource\""))
            .orElseThrow();
        require(split.resourceMetadata().toString().equals("https://civic.example/.well-known/oauth-protected-resource")
            && "invalid_token".equals(split.error()), "parameters of repeated Bearer challenges merge");
        expect(OAuthPolicyException.Reason.INVALID_CHALLENGE, () -> parser.parse(List.of(
            "Bearer resource_metadata=\"https://a.example/m\", Bearer resource_metadata=\"https://b.example/m\"")));
    }

    private static void rejectsMalformedCombinedFields() {
        var parser = new OAuthChallengeParser();
        for (var fields : List.of(
                List.of("", ", scope=read"),
                List.of("Bearer scope=read", "scope=write"),
                List.of("Bearer scope=read", "Bearer scope=write"),
                List.of("Bearer scope=read", "resource_metadata=\"https://mcp.example/meta"),
                List.of("Bearer scope=read", " ".repeat(16384)))) {
            expect(OAuthPolicyException.Reason.INVALID_CHALLENGE, () -> parser.parse(fields));
        }
        var challenge = parser.parse(List.of("", ", , Bearer scope=read,", " , ")).orElseThrow();
        require(challenge.scopes().equals(Set.of("read")), "Leading and trailing empty members ignored across fields");
    }

    private static void expect(OAuthPolicyException.Reason reason, Runnable action) {
        try { action.run(); }
        catch (OAuthPolicyException failure) {
            require(failure.reason() == reason, "Expected typed failure " + reason);
            require(failure.getCause() == null && !failure.getMessage().contains("server-secret"), "Failure is sanitized");
            return;
        }
        throw new AssertionError("Expected " + reason);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
