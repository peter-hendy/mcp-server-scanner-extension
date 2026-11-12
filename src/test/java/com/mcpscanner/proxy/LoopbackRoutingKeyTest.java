package com.mcpscanner.proxy;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LoopbackRoutingKeyTest {

    @Test
    void mintedKeyCarriesAtLeast128BitsOfHex() {
        assertThat(LoopbackRoutingKey.mint().value()).matches("[0-9a-f]{32,}");
    }

    @Test
    void everyMintedKeyIsDifferent() {
        assertThat(LoopbackRoutingKey.mint().value()).isNotEqualTo(LoopbackRoutingKey.mint().value());
    }

    @Test
    void admitsOnlyItsOwnValue() {
        LoopbackRoutingKey key = LoopbackRoutingKey.mint();

        assertThat(key.admits(key.value())).isTrue();
        assertThat(key.admits(LoopbackRoutingKey.mint().value())).isFalse();
        assertThat(key.admits("")).isFalse();
        assertThat(key.admits(null)).isFalse();
    }
}
