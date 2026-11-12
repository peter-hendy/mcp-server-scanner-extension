package com.mcpscanner.checks.shared.auth;

import burp.api.montoya.http.message.requests.HttpRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HostVariantsTest {

    private final HostVariants generator = new HostVariants();

    @Test
    void httpsOn443GetsLocalhostExplicitPortAndReconstructedHost() {
        HttpRequest seed = Seeds.authenticated("https://api.example.com/mcp?tenant=a");
        List<AuthVariant> variants = generator.variantsFor(seed);

        assertThat(variants).extracting(AuthVariant::name)
                .containsExactly("host.localhost", "host.explicit-443", "host.reconstructed");
        assertThat(values(variants, "host.localhost")).containsExactly("localhost", "nearhost");
        assertThat(values(variants, "host.explicit-443"))
                .containsExactly("api.example.com:443", "api.example.com:9443");
        assertThat(values(variants, "host.reconstructed")).containsExactly(
                "api.example.com/?x=1&shape=-_.~", "api.example.com/mcp?x=2&shape=-_.~");
    }

    @Test
    void replacesTheSingleHostHeaderWithoutChangingTheDestination() {
        HttpRequest seed = Seeds.authenticated("https://api.example.com/mcp");

        assertThat(generator.variantsFor(seed)).allSatisfy(variant -> {
            HttpRequest candidate = variant.candidate().applyTo(seed);
            assertThat(candidate.headerValue("Host")).isEqualTo(variant.candidate().value());
            assertThat(candidate.headers()).filteredOn(header -> header.name().equalsIgnoreCase("Host")).hasSize(1);
            assertThat(candidate.httpService().host()).isEqualTo("api.example.com");
            assertThat(variant.family()).isEqualTo(VariantFamily.HOST);
        });
    }

    @Test
    void plainHttpOnACustomPortSkipsTheExplicit443Variant() {
        List<AuthVariant> variants = generator.variantsFor(Seeds.authenticated("http://10.0.0.5:8080/api/mcp"));

        assertThat(variants).extracting(AuthVariant::name).containsExactly("host.localhost", "host.reconstructed");
        assertThat(values(variants, "host.reconstructed")).containsExactly(
                "10.0.0.5:8080/?x=1&shape=-_.~", "10.0.0.5:8080/api/mcp?x=2&shape=-_.~");
    }

    @Test
    void reconstructedShapeListsTheUnusualPathCharacters() {
        List<AuthVariant> variants = generator.variantsFor(Seeds.authenticated("https://api.example.com/v1:mcp+x"));

        assertThat(values(variants, "host.reconstructed").get(0)).isEqualTo("api.example.com/?x=1&shape=-_.~:+");
    }

    @Test
    void rootPathHasNoReconstructedHost() {
        assertThat(generator.variantsFor(Seeds.authenticated("https://api.example.com/")))
                .extracting(AuthVariant::name).containsExactly("host.localhost", "host.explicit-443");
    }

    private static List<String> values(List<AuthVariant> variants, String name) {
        AuthVariant variant = variants.stream().filter(v -> v.name().equals(name)).findFirst().orElseThrow();
        return List.of(variant.candidate().value(), variant.control().orElseThrow().value());
    }
}
