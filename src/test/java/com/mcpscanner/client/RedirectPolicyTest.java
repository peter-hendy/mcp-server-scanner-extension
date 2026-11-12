package com.mcpscanner.client;

import com.mcpscanner.config.RedirectMode;
import com.mcpscanner.logging.McpEventLog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class RedirectPolicyTest {

    private static final URI SOURCE = URI.create("https://origin.example/mcp");
    private static final String SAME_ORIGIN_LOCATION = "/elsewhere";
    private static final String CROSS_ORIGIN_LOCATION = "https://other.example/elsewhere";

    private final McpEventLog eventLog = new McpEventLog(null);

    @Test
    void capsRedirectsAtFive() {
        assertThat(RedirectPolicy.MAX_REDIRECTS).isEqualTo(5);
    }

    @ParameterizedTest(name = "{0} {1} crossOrigin={2} -> follows={3}")
    @MethodSource("modeTable")
    void appliesModeTable(RedirectMode mode, int status, boolean crossOrigin, boolean follows) {
        String location = crossOrigin ? CROSS_ORIGIN_LOCATION : SAME_ORIGIN_LOCATION;

        Optional<RedirectHop> hop = policy(mode).nextHop(status, "GET", SOURCE, Optional.of(location));

        assertThat(hop.isPresent()).isEqualTo(follows);
    }

    static Stream<Arguments> modeTable() {
        Stream.Builder<Arguments> rows = Stream.builder();
        for (int status : new int[] {301, 302, 303, 307, 308}) {
            boolean methodPreserving = status == 307 || status == 308;
            rows.add(Arguments.of(RedirectMode.METHOD_PRESERVING_SAME_ORIGIN, status, false, methodPreserving));
            rows.add(Arguments.of(RedirectMode.METHOD_PRESERVING_SAME_ORIGIN, status, true, false));
            rows.add(Arguments.of(RedirectMode.SAME_ORIGIN, status, false, true));
            rows.add(Arguments.of(RedirectMode.SAME_ORIGIN, status, true, false));
            rows.add(Arguments.of(RedirectMode.ALWAYS, status, false, true));
            rows.add(Arguments.of(RedirectMode.ALWAYS, status, true, true));
        }
        return rows.build();
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 204, 300, 304, 305, 306, 400, 401, 500})
    void ignoresNonRedirectStatuses(int status) {
        assertThat(policy(RedirectMode.ALWAYS).nextHop(status, "GET", SOURCE, Optional.of(SAME_ORIGIN_LOCATION)))
                .isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"301, true", "302, true", "303, true", "307, true", "308, true",
            "200, false", "300, false", "304, false", "306, false"})
    void detectsRedirectStatuses(int status, boolean redirect) {
        assertThat(RedirectPolicy.isRedirect(status)).isEqualTo(redirect);
    }

    @Test
    void ignoresMissingLocation() {
        assertThat(policy(RedirectMode.ALWAYS).nextHop(307, "GET", SOURCE, Optional.empty())).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://user:pass@origin.example/x",
            "/elsewhere#fragment",
            "ftp://origin.example/file",
            "javascript:alert(1)",
            "file:///etc/passwd",
            "https:///no-host",
            "http://bad host/x"
    })
    void rejectsUnsafeOrInvalidLocations(String location) {
        assertThat(policy(RedirectMode.ALWAYS).nextHop(307, "GET", SOURCE, Optional.of(location))).isEmpty();
    }

    @Test
    void resolvesRelativeLocationAgainstSource() {
        Optional<RedirectHop> hop = policy(RedirectMode.SAME_ORIGIN)
                .nextHop(307, "POST", URI.create("https://origin.example/a/b"), Optional.of("c?x=1"));

        assertThat(hop).get().extracting(RedirectHop::target)
                .isEqualTo(URI.create("https://origin.example/a/c?x=1"));
    }

    @Test
    void treatsExplicitDefaultPortRedirectAsSameOriginUnderOff() {
        Optional<RedirectHop> hop = policy(RedirectMode.METHOD_PRESERVING_SAME_ORIGIN)
                .nextHop(307, "POST", SOURCE, Optional.of("https://origin.example:443/next"));

        assertThat(hop).isPresent();
    }

    @ParameterizedTest(name = "{0} {1} -> {2} dropsBodyHeaders={3}")
    @CsvSource({
            "303, POST, GET, true",
            "303, GET, GET, true",
            "303, HEAD, GET, true",
            "301, POST, GET, true",
            "302, POST, GET, true",
            "301, PUT, GET, true",
            "301, GET, GET, false",
            "302, HEAD, HEAD, false",
            "301, head, head, false",
            "307, POST, POST, false",
            "308, POST, POST, false"
    })
    void decidesMethodSwitch(int status, String method, String expectedMethod, boolean dropsBodyHeaders) {
        RedirectHop hop = policy(RedirectMode.ALWAYS)
                .nextHop(status, method, SOURCE, Optional.of(SAME_ORIGIN_LOCATION)).orElseThrow();

        assertThat(hop.method()).isEqualTo(expectedMethod);
        assertThat(hop.switchesToGet()).isEqualTo(dropsBodyHeaders);
    }

    @ParameterizedTest
    @CsvSource({
            "Content-Type, false",
            "content-length, false",
            "Authorization, true",
            "Accept, true"
    })
    void dropsOnlyBodyHeadersOnGetSwitch(String header, boolean kept) {
        RedirectHop switching = policy(RedirectMode.ALWAYS)
                .nextHop(303, "POST", SOURCE, Optional.of(SAME_ORIGIN_LOCATION)).orElseThrow();
        RedirectHop preserving = policy(RedirectMode.ALWAYS)
                .nextHop(307, "POST", SOURCE, Optional.of(SAME_ORIGIN_LOCATION)).orElseThrow();

        assertThat(switching.keepsHeader(header)).isEqualTo(kept);
        assertThat(preserving.keepsHeader(header)).isTrue();
    }

    @Test
    void readsModeAtDecisionTime() {
        AtomicReference<RedirectMode> mode = new AtomicReference<>(RedirectMode.METHOD_PRESERVING_SAME_ORIGIN);
        RedirectPolicy policy = new RedirectPolicy(eventLog, mode::get);

        assertThat(policy.nextHop(302, "GET", SOURCE, Optional.of(SAME_ORIGIN_LOCATION))).isEmpty();
        mode.set(RedirectMode.SAME_ORIGIN);
        assertThat(policy.nextHop(302, "GET", SOURCE, Optional.of(SAME_ORIGIN_LOCATION))).isPresent();
    }

    @Test
    void replacedModeSupplierTakesEffect() {
        RedirectPolicy policy = policy(RedirectMode.METHOD_PRESERVING_SAME_ORIGIN);
        policy.setModeSupplier(() -> RedirectMode.ALWAYS);

        assertThat(policy.nextHop(302, "GET", SOURCE, Optional.of(CROSS_ORIGIN_LOCATION))).isPresent();
    }

    @Test
    void exposesCurrentMode() {
        assertThat(policy(RedirectMode.SAME_ORIGIN).mode()).isEqualTo(RedirectMode.SAME_ORIGIN);
    }

    @Test
    void defaultsToOff() {
        RedirectPolicy policy = new RedirectPolicy(eventLog);

        assertThat(policy.nextHop(302, "GET", SOURCE, Optional.of(SAME_ORIGIN_LOCATION))).isEmpty();
        assertThat(policy.nextHop(307, "GET", SOURCE, Optional.of(SAME_ORIGIN_LOCATION))).isPresent();
    }

    @Test
    void warnsWhenAllowingCrossOriginHop() {
        policy(RedirectMode.ALWAYS).nextHop(302, "GET", SOURCE, Optional.of("http://other.example:8080/x"));

        assertThat(warnings()).containsExactly(
                "Cross-origin redirect allowed: https://origin.example -> http://other.example:8080");
    }

    @Test
    void doesNotWarnForSameOriginOrRefusedHops() {
        policy(RedirectMode.ALWAYS).nextHop(302, "GET", SOURCE, Optional.of(SAME_ORIGIN_LOCATION));
        policy(RedirectMode.SAME_ORIGIN).nextHop(302, "GET", SOURCE, Optional.of(CROSS_ORIGIN_LOCATION));

        assertThat(warnings()).isEmpty();
    }

    @Test
    void warnsWhenFinalResponseIsCrossOrigin() {
        RedirectPolicy policy = policy(RedirectMode.METHOD_PRESERVING_SAME_ORIGIN);

        policy.warnIfCrossOriginFollowed(SOURCE, URI.create("https://origin.example:443/same"));
        policy.warnIfCrossOriginFollowed(SOURCE, null);
        policy.warnIfCrossOriginFollowed(SOURCE, URI.create("https://other.example/x"));

        assertThat(warnings()).containsExactly(
                "Cross-origin redirect followed: https://origin.example -> https://other.example");
    }

    private RedirectPolicy policy(RedirectMode mode) {
        return new RedirectPolicy(eventLog, () -> mode);
    }

    private List<String> warnings() {
        return eventLog.snapshot().stream()
                .filter(entry -> entry.level() == McpEventLog.Level.WARN)
                .map(McpEventLog.LogEntry::message)
                .toList();
    }
}
