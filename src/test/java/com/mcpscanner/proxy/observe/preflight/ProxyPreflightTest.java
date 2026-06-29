package com.mcpscanner.proxy.observe.preflight;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.burpsuite.BurpSuite;
import burp.api.montoya.core.Version;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProxyPreflightTest {

    private static final int DEFAULT_PORT = 8080;

    @Test
    void burpVersionAtOrAboveThresholdIsOk() {
        ProxyPreflight preflight = preflightWith(version("2025.8"), reachable());

        PreflightResult result = resultFor(preflight, BurpVersionCheck.LABEL);

        assertThat(result.status()).isEqualTo(PreflightStatus.OK);
    }

    @Test
    void newerBurpVersionIsOk() {
        ProxyPreflight preflight = preflightWith(version("2026.2"), reachable());

        assertThat(resultFor(preflight, BurpVersionCheck.LABEL).status())
                .isEqualTo(PreflightStatus.OK);
    }

    @Test
    void laterReleaseInSameYearIsOk() {
        ProxyPreflight preflight = preflightWith(version("2025.10"), reachable());

        assertThat(resultFor(preflight, BurpVersionCheck.LABEL).status())
                .isEqualTo(PreflightStatus.OK);
    }

    @Test
    void earlierReleaseInSameYearIsFail() {
        ProxyPreflight preflight = preflightWith(version("2025.7"), reachable());

        PreflightResult result = resultFor(preflight, BurpVersionCheck.LABEL);

        assertThat(result.status()).isEqualTo(PreflightStatus.FAIL);
        assertThat(result.message()).contains("2025.8");
    }

    @Test
    void earlierYearIsFail() {
        ProxyPreflight preflight = preflightWith(version("2024.12"), reachable());

        assertThat(resultFor(preflight, BurpVersionCheck.LABEL).status())
                .isEqualTo(PreflightStatus.FAIL);
    }

    @Test
    void unparseableVersionIsWarnNotFail() {
        ProxyPreflight preflight = preflightWith(version("dev-snapshot"), reachable());

        assertThat(resultFor(preflight, BurpVersionCheck.LABEL).status())
                .isEqualTo(PreflightStatus.WARN);
    }

    @Test
    void nullVersionIsWarnNotFail() {
        ProxyPreflight preflight = preflightWith(version(null), reachable());

        assertThat(resultFor(preflight, BurpVersionCheck.LABEL).status())
                .isEqualTo(PreflightStatus.WARN);
    }

    @Test
    void threeComponentVersionIsOk() {
        ProxyPreflight preflight = preflightWith(version("2025.8.1"), reachable());

        assertThat(resultFor(preflight, BurpVersionCheck.LABEL).status())
                .isEqualTo(PreflightStatus.OK);
    }

    @Test
    void reachableListenerIsOk() {
        ProxyPreflight preflight = preflightWith(version("2025.8"), reachable());

        assertThat(resultFor(preflight, ProxyListenerReachableCheck.LABEL).status())
                .isEqualTo(PreflightStatus.OK);
    }

    @Test
    void unreachableListenerIsFailWithListenerSpecificMessage() {
        ProxyPreflight preflight = preflightWith(version("2025.8"), unreachable());

        PreflightResult result = resultFor(preflight, ProxyListenerReachableCheck.LABEL);

        assertThat(result.status()).isEqualTo(PreflightStatus.FAIL);
        assertThat(result.message()).containsIgnoringCase("proxy listener");
        assertThat(result.message()).doesNotContainIgnoringCase("MCP server");
    }

    @Test
    void customListenerPortIsProbedNotTheDefault() {
        int customPort = 9999;
        Predicate<InetSocketAddress> probe = address -> address.getPort() == customPort;

        ProxyPreflight preflight = new ProxyPreflight(
                apiReturning(version("2025.8")), probe::test, customPort);

        assertThat(resultFor(preflight, ProxyListenerReachableCheck.LABEL).status())
                .isEqualTo(PreflightStatus.OK);
    }

    @Test
    void listenerProbeTargetsLoopback() {
        int[] probedPort = {-1};
        String[] probedHost = {null};
        PortProbe recordingProbe = address -> {
            probedHost[0] = address.getHostString();
            probedPort[0] = address.getPort();
            return true;
        };

        ProxyPreflight preflight = new ProxyPreflight(
                apiReturning(version("2025.8")), recordingProbe, DEFAULT_PORT);
        preflight.run();

        assertThat(probedHost[0]).isEqualTo("127.0.0.1");
        assertThat(probedPort[0]).isEqualTo(DEFAULT_PORT);
    }

    @Test
    void streamingSettingsItemIsWarnNamingTheSetting() {
        PreflightResult result = resultFor(defaultPreflight(), StreamingSettingsReminder.LABEL);

        assertThat(result.status()).isEqualTo(PreflightStatus.WARN);
        assertThat(result.message()).contains("Streaming responses");
        assertThat(result.message()).contains("text/event-stream");
    }

    @Test
    void tlsPassThroughItemIsWarnNamingTheAction() {
        PreflightResult result = resultFor(defaultPreflight(), TlsPassThroughReminder.LABEL);

        assertThat(result.status()).isEqualTo(PreflightStatus.WARN);
        assertThat(result.message()).containsIgnoringCase("TLS pass-through");
    }

    @Test
    void caTrustItemIsWarnNamingTheAction() {
        PreflightResult result = resultFor(defaultPreflight(), CaTrustReminder.LABEL);

        assertThat(result.status()).isEqualTo(PreflightStatus.WARN);
        assertThat(result.message()).containsIgnoringCase("CA");
    }

    @Test
    void plaintextEndpointItemIsWarnNamingTheAction() {
        PreflightResult result = resultFor(defaultPreflight(), PlaintextEndpointReminder.LABEL);

        assertThat(result.status()).isEqualTo(PreflightStatus.WARN);
        assertThat(result.message()).containsIgnoringCase("http://127.0.0.1");
    }

    @Test
    void aggregatesEveryCheck() {
        List<String> labels = defaultPreflight().run().results().stream()
                .map(LabelledResult::label)
                .toList();

        assertThat(labels).containsExactlyInAnyOrder(
                BurpVersionCheck.LABEL,
                ProxyListenerReachableCheck.LABEL,
                StreamingSettingsReminder.LABEL,
                TlsPassThroughReminder.LABEL,
                CaTrustReminder.LABEL,
                PlaintextEndpointReminder.LABEL);
    }

    @Test
    void reportHasFailuresWhenAnyCheckFails() {
        PreflightReport report = preflightWith(version("2020.1"), reachable()).run();

        assertThat(report.hasFailures()).isTrue();
    }

    @Test
    void reportHasNoFailuresWhenOnlyWarnsAndOks() {
        PreflightReport report = defaultPreflight().run();

        assertThat(report.hasFailures()).isFalse();
    }

    private ProxyPreflight defaultPreflight() {
        return preflightWith(version("2025.8"), reachable());
    }

    private ProxyPreflight preflightWith(Version version, PortProbe probe) {
        return new ProxyPreflight(apiReturning(version), probe, DEFAULT_PORT);
    }

    private static PreflightResult resultFor(ProxyPreflight preflight, String label) {
        return preflight.run().results().stream()
                .filter(result -> result.label().equals(label))
                .map(LabelledResult::result)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No preflight result for label: " + label));
    }

    private static MontoyaApi apiReturning(Version version) {
        MontoyaApi api = mock(MontoyaApi.class);
        BurpSuite burpSuite = mock(BurpSuite.class);
        lenient().when(api.burpSuite()).thenReturn(burpSuite);
        lenient().when(burpSuite.version()).thenReturn(version);
        return api;
    }

    private static Version version(String major) {
        Version version = mock(Version.class);
        lenient().when(version.major()).thenReturn(major);
        return version;
    }

    private static PortProbe reachable() {
        return address -> true;
    }

    private static PortProbe unreachable() {
        return address -> false;
    }
}
