package com.mcpscanner.mcp;

import burp.api.montoya.core.Version;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BurpNativeJsonRpcFieldsTest {

    private static final byte[] TOOL_CALL = ("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\","
            + "\"params\":{\"name\":\"t\",\"arguments\":{\"a\":1}}}").getBytes(StandardCharsets.UTF_8);

    @ParameterizedTest
    @ValueSource(longs = {20261100000055065L, 20261100000999999L, 20261101000000001L, 20261200000000001L,
            20270100000000001L})
    void burpFromTheFirstNightlyWithNativeMcpScanningHasThem(long buildNumber) {
        assertThat(BurpNativeJsonRpcFields.detectedIn(() -> version(buildNumber)).present()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(longs = {20261100000055064L, 20261001000054000L, 20260700000051093L, 0L, -1L})
    void olderOrUnknownBurpHasNone(long buildNumber) {
        assertThat(BurpNativeJsonRpcFields.detectedIn(() -> version(buildNumber)).present()).isFalse();
    }

    @Test
    void unreadableVersionCountsAsOld() {
        Version broken = mock(Version.class);
        when(broken.buildNumber()).thenThrow(new IllegalStateException("unparseable"));

        assertThat(BurpNativeJsonRpcFields.detectedIn(() -> broken).present()).isFalse();
        assertThat(BurpNativeJsonRpcFields.detectedIn(() -> null).present()).isFalse();
        assertThat(BurpNativeJsonRpcFields.detectedIn(() -> {
            throw new IllegalStateException("no version");
        }).present()).isFalse();
    }

    @Test
    void argumentsAreOnlyOfferedWhenBurpHasNativeFields() {
        assertThat(BurpNativeJsonRpcFields.PRESENT.argumentsIn(TOOL_CALL)).isPresent();
        assertThat(BurpNativeJsonRpcFields.ABSENT.argumentsIn(TOOL_CALL)).isEmpty();
    }

    private static Version version(long buildNumber) {
        Version version = mock(Version.class);
        when(version.buildNumber()).thenReturn(buildNumber);
        return version;
    }
}
