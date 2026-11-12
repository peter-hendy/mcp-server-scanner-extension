package com.mcpscanner.checks.catalog.jwtvalidation;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ForgeryStrategiesTest {

    private static final CompactJwt RS256 = CompactJwt.parse(TestTokens.RS256_TOKEN).orElseThrow();
    private static final CompactJwt HS256 = CompactJwt.parse(TestTokens.HS256_TOKEN).orElseThrow();
    private static final PublicKeyMaterial PEM = new PublicKeyMaterial("PEM", "-----BEGIN PUBLIC KEY-----\n"
            .getBytes(StandardCharsets.US_ASCII));

    private final AtomicInteger discoveries = new AtomicInteger();

    private ForgeryContext context(CompactJwt captured, List<PublicKeyMaterial> keys) {
        return new ForgeryContext(captured, () -> {
            discoveries.incrementAndGet();
            return keys;
        });
    }

    @Test
    void randomKeyStrategyYieldsOneUncontrolledRs256Attempt() {
        List<ForgeryAttempt> attempts = new RandomKeyForgery().attemptsFor(context(RS256, List.of()));

        assertThat(attempts).singleElement().satisfies(attempt -> {
            assertThat(attempt.candidate()).isInstanceOf(RandomRsaSignature.class);
            assertThat(attempt.wrongKeyControl()).isEmpty();
        });
    }

    @Test
    void algNoneStrategyYieldsOneUncontrolledUnsignedAttempt() {
        assertThat(new AlgNoneForgery().attemptsFor(context(HS256, List.of()))).singleElement()
                .satisfies(attempt -> assertThat(attempt.candidate()).isInstanceOf(UnsignedSignature.class));
    }

    @Test
    void publicKeyStrategyKeysHs256WithEveryDiscoveredEncodingAndARandomKeyControl() {
        PublicKeyMaterial other = new PublicKeyMaterial("PEM without newline", new byte[]{1, 2});
        List<ForgeryAttempt> attempts = new PublicKeyAsHmacSecretForgery().attemptsFor(context(RS256, List.of(PEM, other)));

        assertThat(attempts).hasSize(2);
        assertThat(attempts).allSatisfy(attempt -> {
            String token = attempt.candidate().sign(claims());
            assertThat(TestTokens.headerOf(token).path("alg").asText()).isEqualTo("HS256");
            assertThat(TestTokens.headerOf(token).path("kid").asText()).isEqualTo("live-key");
            String control = attempt.wrongKeyControl().orElseThrow().sign(claims());
            assertThat(TestTokens.headerOf(control)).isEqualTo(TestTokens.headerOf(token));
            assertThat(TestTokens.signatureOf(control)).isNotEqualTo(TestTokens.signatureOf(token));
        });
        assertThat(attempts.get(0).candidate().description()).contains("PEM");
        assertThat(TestTokens.signatureOf(attempts.get(0).candidate().sign(claims()))).isEqualTo(JwtSegments.encode(
                HmacSha256.digest(PEM.bytes(), TestTokens.signingInputOf(attempts.get(0).candidate().sign(claims()))
                        .getBytes(StandardCharsets.US_ASCII))));
    }

    @Test
    void publicKeyStrategyDoesNotDiscoverKeysForSymmetricTokens() {
        assertThat(new PublicKeyAsHmacSecretForgery().attemptsFor(context(HS256, List.of(PEM)))).isEmpty();
        assertThat(discoveries).hasValue(0);
    }

    @Test
    void publicKeyStrategyYieldsNothingWithoutDiscoveredKeys() {
        assertThat(new PublicKeyAsHmacSecretForgery().attemptsFor(context(RS256, List.of()))).isEmpty();
    }

    @Test
    void emptySecretStrategyUsesAnUnknownKeyIdAndAnEmptyHmacKeyWithARandomKeyControl() {
        ForgeryAttempt attempt = new EmptySecretUnknownKeyIdForgery().attemptsFor(context(HS256, List.of()))
                .get(0);
        String token = attempt.candidate().sign(claims());
        ObjectNode header = TestTokens.headerOf(token);

        assertThat(header.path("alg").asText()).isEqualTo("HS256");
        assertThat(header.path("kid").asText()).startsWith("mcp-scanner-unknown-").isNotEqualTo("real-kid");
        assertThat(TestTokens.signatureOf(token)).isEqualTo(JwtSegments.encode(HmacSha256.digest(new byte[0],
                TestTokens.signingInputOf(token).getBytes(StandardCharsets.US_ASCII))));
        String control = attempt.wrongKeyControl().orElseThrow().sign(claims());
        assertThat(TestTokens.headerOf(control)).isEqualTo(header);
        assertThat(TestTokens.signatureOf(control)).isNotEqualTo(TestTokens.signatureOf(token));
    }

    @Test
    void emptySecretStrategyAlsoRunsForAsymmetricTokens() {
        assertThat(new EmptySecretUnknownKeyIdForgery().attemptsFor(context(RS256, List.of()))).hasSize(1);
    }

    @Test
    void defaultCatalogOrdersTheStrategiesFromCheapestToMostSpecific() {
        List<ForgeryAttempt> attempts = ForgeryCatalog.defaultCatalog().attemptsFor(context(RS256, List.of(PEM)));

        assertThat(attempts).extracting(attempt -> attempt.candidate().getClass().getSimpleName())
                .containsExactly("RandomRsaSignature", "UnsignedSignature", "HmacSignature", "HmacSignature");
        assertThat(discoveries).hasValue(1);
    }

    @Test
    void contextDiscoversPublicKeysAtMostOnce() {
        ForgeryContext context = context(RS256, List.of(PEM));

        context.publicKeys();
        context.publicKeys();

        assertThat(discoveries).hasValue(1);
    }

    private static ObjectNode claims() {
        return TestTokens.object("{\"sub\":\"alice\"}");
    }
}
