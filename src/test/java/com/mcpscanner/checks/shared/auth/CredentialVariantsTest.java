package com.mcpscanner.checks.shared.auth;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.auth.BearerTokenAuthStrategy;
import com.mcpscanner.auth.CustomHeaderAuthStrategy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CredentialVariantsTest {

    private final HttpRequest seed = Seeds.authenticated("https://api.example.com/mcp");

    @Test
    void bearerStrategyGetsTheAuthorizationMatrix() {
        CredentialVariants generator = new CredentialVariants(() -> new BearerTokenAuthStrategy("t"), Seeds.CANARY);

        List<AuthVariant> variants = generator.variantsFor(seed);

        assertThat(variants).extracting(AuthVariant::name).containsExactly(
                "credential.none", "credential.empty-bearer", "credential.garbage-bearer",
                "credential.no-scheme", "credential.basic-canary");
        assertThat(variants).extracting(variant -> variant.candidate().value()).containsExactly(
                "", "Bearer ", "Bearer not_a_real_token_12345", "not_a_real_token_12345", "Basic c4n4ry");
        assertThat(variants).allSatisfy(variant -> assertThat(variant.family()).isEqualTo(VariantFamily.CREDENTIAL));
    }

    @Test
    void customHeaderStrategyAlsoGetsEmptyAndGarbageValuesOfItsHeader() {
        CredentialVariants generator = new CredentialVariants(
                () -> new CustomHeaderAuthStrategy(Map.of("X-Api-Key", "k")), Seeds.CANARY);

        List<AuthVariant> variants = generator.variantsFor(seed);

        assertThat(variants).extracting(AuthVariant::name)
                .endsWith("credential.empty-x-api-key", "credential.garbage-x-api-key");
        assertThat(variants.get(variants.size() - 1).candidate().applyTo(seed).headerValue("X-Api-Key"))
                .isEqualTo("not_a_real_credential_12345");
        assertThat(variants.get(variants.size() - 2).candidate().applyTo(seed).headerValue("X-Api-Key"))
                .isEmpty();
    }

    @Test
    void controlsAreNoCredentialsAndAMalformedCredentialOfTheActiveScheme() {
        CredentialVariants bearer = new CredentialVariants(() -> new BearerTokenAuthStrategy("t"), Seeds.CANARY);
        CredentialVariants custom = new CredentialVariants(() -> new CustomHeaderAuthStrategy(Map.of("X-Api-Key", "k")),
                Seeds.CANARY);

        assertThat(bearer.noCredentials().name()).isEqualTo("credential.none");
        assertThat(bearer.malformedCredential().name()).isEqualTo("credential.garbage-bearer");
        assertThat(custom.malformedCredential().name()).isEqualTo("credential.garbage-x-api-key");
    }

    @Test
    void fabricatedCredentialIsACanaryValueInTheMalformedControlsSlot() {
        AuthVariant bearer = new CredentialVariants(() -> new BearerTokenAuthStrategy("t"), Seeds.CANARY)
                .fabricatedCredential();
        AuthVariant custom = new CredentialVariants(() -> new CustomHeaderAuthStrategy(Map.of("X-Api-Key", "k")),
                Seeds.CANARY).fabricatedCredential();

        assertThat(bearer.name()).isEqualTo("credential.fabricated-bearer");
        assertThat(bearer.candidate().applyTo(seed).headerValue("Authorization")).isEqualTo("Bearer invalid_c4n4ry");
        assertThat(custom.name()).isEqualTo("credential.fabricated-x-api-key");
        assertThat(custom.candidate().applyTo(seed).headerValue("X-Api-Key")).isEqualTo("invalid_c4n4ry");
    }

    @Test
    void cookieContributedByAStrategyIsNotTreatedAsACustomHeader() {
        CredentialVariants generator = new CredentialVariants(
                () -> new CustomHeaderAuthStrategy(Map.of("Cookie", "a=b")), Seeds.CANARY);

        assertThat(generator.variantsFor(seed)).extracting(AuthVariant::name).noneMatch(name -> name.contains("cookie"));
    }
}
