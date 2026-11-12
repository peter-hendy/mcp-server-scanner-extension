package com.mcpscanner.checks.catalog.unauthenticatedrequestexecution;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.auth.AuthStrategy;
import com.mcpscanner.auth.BearerTokenAuthStrategy;
import com.mcpscanner.checks.shared.auth.AuthVariant;
import com.mcpscanner.checks.shared.auth.AuthVariantSuite;
import com.mcpscanner.checks.shared.auth.CredentialStripper;
import com.mcpscanner.checks.shared.auth.VariantFamily;
import com.mcpscanner.checks.shared.probe.SimulatedMcpServer;
import com.mcpscanner.testutil.ImmutableHttpRequest;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class SweepVariantsTest {

    private final Supplier<AuthStrategy> strategy = () -> new BearerTokenAuthStrategy("t");
    private final SweepVariants sweep = sweepFor(SweepFamilies.forDirectDelivery());

    private SweepVariants sweepFor(Set<VariantFamily> families) {
        return new SweepVariants(new AuthVariantSuite(strategy, "canary"),
                new RetainedSessionVariant(new CredentialStripper(strategy)), families);
    }

    @Test
    void retainedSessionComesFirstAndTheNoCredentialControlIsNotACandidate() {
        HttpRequest seed = ImmutableHttpRequest.post("http://mcp.example/mcp",
                Map.of("Authorization", "Bearer t", "Mcp-Session-Id", "live"), SimulatedMcpServer.toolCall());

        assertThat(sweep.variantsFor(seed)).first().extracting(AuthVariant::name).isEqualTo(RetainedSessionVariant.NAME);
        assertThat(sweep.variantsFor(seed)).extracting(AuthVariant::name).doesNotContain("credential.none");
    }

    @Test
    void everySharedVariantFamilyIsSwept() {
        HttpRequest seed = ImmutableHttpRequest.post("http://mcp.example/mcp", Map.of("Authorization", "Bearer t"),
                SimulatedMcpServer.toolCall());

        assertThat(sweep.variantsFor(seed)).extracting(AuthVariant::family)
                .contains(VariantFamily.values());
    }

    @Test
    void streamProxiedSweepLeavesOutPathAndSessionVariantsBecauseTheirControlsWouldBypassTheProxy() {
        HttpRequest seed = ImmutableHttpRequest.post("http://mcp.example/messages?sessionId=authenticated",
                Map.of("Authorization", "Bearer t"), SimulatedMcpServer.toolCall());

        assertThat(sweepFor(SweepFamilies.forStreamProxy()).variantsFor(seed)).extracting(AuthVariant::family)
                .doesNotContain(VariantFamily.PATH, VariantFamily.SESSION)
                .contains(VariantFamily.CREDENTIAL, VariantFamily.ROUTING_HEADER, VariantFamily.HOST);
    }

    @Test
    void noStreamProxiedCandidateOrControlLeavesTheProxyRoute() {
        HttpRequest seed = ImmutableHttpRequest.post("http://mcp.example/messages?sessionId=authenticated",
                Map.of("Authorization", "Bearer t"), SimulatedMcpServer.toolCall());

        assertThat(sweepFor(SweepFamilies.forStreamProxy()).variantsFor(seed)).allSatisfy(variant -> {
            assertThat(variant.candidate().applyTo(seed).path()).as(variant.name()).isEqualTo(seed.path());
            variant.control().ifPresent(control ->
                    assertThat(control.applyTo(seed).path()).as(variant.name() + " control").isEqualTo(seed.path()));
        });
    }
}
