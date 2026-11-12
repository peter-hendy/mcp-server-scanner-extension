package com.mcpscanner.checks.catalog.oauthredirectpolicy;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryCaseTest {

    private static final URI CANDIDATE = URI.create("https://client.example.com/other");

    @Test
    void plainCaseDoesNotNormalise() {
        assertThat(DeliveryCase.of("shape", CANDIDATE, AuthorizationVariant.SILENT))
                .isEqualTo(new DeliveryCase("shape", CANDIDATE, AuthorizationVariant.SILENT, false));
    }

    @Test
    void normalisingCaseUsesTheStandardVariant() {
        assertThat(DeliveryCase.normalising("shape", CANDIDATE))
                .isEqualTo(new DeliveryCase("shape", CANDIDATE, AuthorizationVariant.STANDARD, true));
    }

    @Test
    void describesCodeDelivery() {
        assertThat(DeliveryCase.of("other path", CANDIDATE, AuthorizationVariant.FRAGMENT).describe(Delivery.CODE))
                .isEqualTo("authorization code delivered to https://client.example.com/other "
                        + "(other path; response_mode=fragment)");
    }

    @Test
    void describesNormalisingDeliveryAsReachingTheCandidateOrItsNormalisedForm() {
        assertThat(DeliveryCase.normalising("dot-segment path", CANDIDATE).describe(Delivery.CODE))
                .isEqualTo("authorization code delivered to https://client.example.com/other or the URI it "
                        + "normalises to (dot-segment path; standard authorization request)");
    }

    @Test
    void describesErrorDelivery() {
        assertThat(DeliveryCase.of("other path", CANDIDATE, AuthorizationVariant.STANDARD).describe(Delivery.ERROR))
                .startsWith("OAuth error delivered to ");
    }
}
