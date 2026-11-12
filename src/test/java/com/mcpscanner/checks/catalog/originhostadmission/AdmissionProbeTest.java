package com.mcpscanner.checks.catalog.originhostadmission;

import burp.api.montoya.http.message.requests.HttpRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AdmissionProbeTest {

    private final HttpRequest seed = AdmissionServers.toolsList(AdmissionServers.LOOPBACK_URL)
            .withAddedHeader("Origin", "http://127.0.0.1:8765");

    @Test
    void theStandardSetHasThreeOriginProbesOneHostProbeAndOneCombinedProbe() {
        assertThat(AdmissionProbe.STANDARD_SET).extracting(AdmissionProbe::category).containsExactly(
                AdmissionCategory.ORIGIN, AdmissionCategory.ORIGIN, AdmissionCategory.ORIGIN,
                AdmissionCategory.HOST, AdmissionCategory.HOST_AND_ORIGIN);
    }

    @Test
    void originProbesReplaceOnlyTheOriginAndKeepTheCapturedHost() {
        assertThat(AdmissionProbe.STANDARD_SET.subList(0, 3)).allSatisfy(probe -> {
            HttpRequest probed = probe.applyTo(seed);
            assertThat(probed.headerValue("Host")).isEqualTo("127.0.0.1:8765");
            assertThat(probed.headerValue("Origin")).isNotEqualTo("http://127.0.0.1:8765");
        });
        assertThat(AdmissionProbe.STANDARD_SET.subList(0, 3)).extracting(probe -> probe.applyTo(seed).headerValue("Origin"))
                .containsExactly("http://evil.example", "null", "http://attacker.example:1337");
    }

    @Test
    void theHostProbeReplacesOnlyTheHostAndKeepsTheCapturedOrigin() {
        HttpRequest probed = AdmissionProbe.STANDARD_SET.get(3).applyTo(seed);

        assertThat(probed.headerValue("Host")).isEqualTo("attacker.example");
        assertThat(probed.headerValue("Origin")).isEqualTo("http://127.0.0.1:8765");
    }

    @Test
    void theCombinedProbeSendsTheMatchingPairABrowserSendsToAReboundName() {
        HttpRequest probed = AdmissionProbe.STANDARD_SET.get(4).applyTo(seed);

        assertThat(probed.headerValue("Host")).isEqualTo("attacker.example");
        assertThat(probed.headerValue("Origin")).isEqualTo("http://attacker.example");
    }

    @Test
    void probesNeverChangeTheBodyMethodOrDestination() {
        assertThat(AdmissionProbe.STANDARD_SET).allSatisfy(probe -> {
            HttpRequest probed = probe.applyTo(seed);
            assertThat(probed.bodyToString()).isEqualTo(seed.bodyToString());
            assertThat(probed.method()).isEqualTo(seed.method());
            assertThat(probed.httpService()).isSameAs(seed.httpService());
        });
    }

    @Test
    void everyProbeHasADistinctLabel() {
        assertThat(AdmissionProbe.STANDARD_SET).extracting(AdmissionProbe::label).doesNotHaveDuplicates();
    }
}
