package com.mcpscanner.checks.shared.auth;

import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.auth.AuthStrategy;
import com.mcpscanner.auth.CurrentAuthHolder;
import com.mcpscanner.auth.CustomHeaderAuthStrategy;
import com.mcpscanner.mcp.ScannerSentinels;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

class AuthVariantSuiteTest {

    private final HttpRequest seed = Seeds.authenticated("https://api.example.com/mcp?tenant=a");
    private final AuthStrategy strategy = spy(new CustomHeaderAuthStrategy(Map.of("X-Api-Key", "real-key")));
    private final CurrentAuthHolder holder = new CurrentAuthHolder();
    private final AuthVariantSuite suite = suiteFor(strategy);

    private AuthVariantSuite suiteFor(AuthStrategy active) {
        holder.set(active);
        return new AuthVariantSuite(holder, Seeds.CANARY);
    }

    @Test
    void producesEveryFamily() {
        assertThat(suite.variantsFor(seed)).extracting(AuthVariant::family).containsOnly(VariantFamily.values())
                .contains(VariantFamily.values());
    }

    @Test
    void producesOnlyTheRequestedFamilies() {
        assertThat(suite.variantsFor(seed, EnumSet.of(VariantFamily.PATH, VariantFamily.HOST)))
                .extracting(AuthVariant::family).containsOnly(VariantFamily.PATH, VariantFamily.HOST);
    }

    @Test
    void nonCredentialVariantsCarryNoCredentialOrLiveSessionAndAskTheProxyNotToReinjectThem() {
        List<HttpRequest> requests = everyRequest(suite.variantsFor(seed,
                EnumSet.complementOf(EnumSet.of(VariantFamily.CREDENTIAL, VariantFamily.SESSION))));

        assertThat(requests).isNotEmpty().allSatisfy(request -> {
            assertThat(request.headerValue("Authorization")).isNull();
            assertThat(request.headerValue("Cookie")).isNull();
            assertThat(request.headerValue("X-Api-Key")).isNull();
            assertThat(request.headerValue("Mcp-Session-Id")).isNull();
            assertThat(request.headerValue(ScannerSentinels.STRIP_AUTH_HEADER)).isEqualTo("1");
        });
    }

    @Test
    void credentialVariantsCarryOnlyTheirOwnCredential() {
        List<AuthVariant> variants = suite.variantsFor(seed, EnumSet.of(VariantFamily.CREDENTIAL));
        HttpRequest garbageBearer = named(variants, "credential.garbage-bearer").candidate().applyTo(seed);
        HttpRequest none = named(variants, "credential.none").candidate().applyTo(seed);

        assertThat(garbageBearer.headerValue("Authorization")).isEqualTo("Bearer not_a_real_token_12345");
        assertThat(garbageBearer.headerValue("X-Api-Key")).isNull();
        assertThat(garbageBearer.headerValue("Cookie")).isNull();
        assertThat(identityHeaders(none)).isEmpty();
    }

    @Test
    void noVariantOrControlReusesTheLiveSessionIdOrChangesTheMethod() {
        Stream<HttpRequest> all = Stream.concat(everyRequest(suite.variantsFor(seed)).stream(),
                everyRequest(controls()).stream());

        assertThat(all).allSatisfy(request -> {
            assertThat(request.headerValue("Mcp-Session-Id")).isNotEqualTo(Seeds.LIVE_SESSION);
            assertThat(request.method()).isEqualTo("POST");
        });
    }

    @Test
    void generatingAndApplyingVariantsNeverRefreshesOrReplacesTheLiveAuth() {
        everyRequest(suite.variantsFor(seed));
        everyRequest(controls());

        verify(strategy, never()).refresh();
        verify(strategy, never()).isTerminallyFailed();
        assertThat(holder.get()).isSameAs(strategy);
        assertThat(strategy.headers()).containsExactly(Map.entry("X-Api-Key", "real-key"));
    }

    @Test
    void seedIsLeftUntouched() {
        everyRequest(suite.variantsFor(seed));

        assertThat(seed.headerValue("Authorization")).isEqualTo("Bearer real-token");
        assertThat(seed.headerValue("Mcp-Session-Id")).isEqualTo(Seeds.LIVE_SESSION);
        assertThat(seed.path()).isEqualTo("/mcp?tenant=a");
    }

    @Test
    void refusalControlsAreAnonymousNoCredentialAndMalformedCredential() {
        List<AuthVariant> controls = controls();

        assertThat(controls).extracting(AuthVariant::name)
                .containsExactly("credential.none", "credential.garbage-x-api-key");
        HttpRequest malformed = controls.get(1).candidate().applyTo(seed);
        assertThat(malformed.headerValue("X-Api-Key")).isEqualTo("not_a_real_credential_12345");
        assertThat(malformed.headerValue("Authorization")).isNull();
        assertThat(malformed.headerValue("Mcp-Session-Id")).isNull();
    }

    @Test
    void namedControlsAndTheFabricatedCredentialAreStrippedOfTheSeedsCredentialsAndSession() {
        assertThat(suite.noCredentialControl().name()).isEqualTo("credential.none");
        assertThat(suite.malformedCredentialControl().name()).isEqualTo("credential.garbage-x-api-key");
        HttpRequest fabricated = suite.fabricatedCredential().candidate().applyTo(seed);

        assertThat(fabricated.headerValue("X-Api-Key")).isEqualTo("invalid_" + Seeds.CANARY);
        assertThat(fabricated.headerValue("Authorization")).isNull();
        assertThat(fabricated.headerValue("Mcp-Session-Id")).isNull();
    }

    @Test
    void readsTheActiveStrategyAtGenerationTime() {
        holder.set(new CustomHeaderAuthStrategy(Map.of("X-Other-Key", "v")));

        assertThat(controls()).extracting(AuthVariant::name)
                .containsExactly("credential.none", "credential.garbage-x-other-key");
    }

    private static List<HttpRequest> everyRequest(List<AuthVariant> variants) {
        return variants.stream().flatMap(variant -> Stream.concat(
                        Stream.of(variant.candidate()), variant.control().stream()))
                .map(mutation -> mutation.applyTo(Seeds.authenticated("https://api.example.com/mcp?tenant=a")))
                .toList();
    }

    private static List<HttpHeader> identityHeaders(HttpRequest request) {
        return request.headers().stream().filter(header -> Stream.of("Authorization", "Cookie", "X-Api-Key",
                "Mcp-Session-Id").anyMatch(name -> name.equalsIgnoreCase(header.name()))).toList();
    }

    private static AuthVariant named(List<AuthVariant> variants, String name) {
        return variants.stream().filter(variant -> variant.name().equals(name)).findFirst().orElseThrow();
    }

    private List<AuthVariant> controls() {
        return List.of(suite.noCredentialControl(), suite.malformedCredentialControl());
    }
}
