package com.mcpscanner.checks.catalog.oauthredirectpolicy;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryTest {

    @Test
    void codeAndErrorAreDelivered() {
        assertThat(Delivery.CODE.delivered()).isTrue();
        assertThat(Delivery.ERROR.delivered()).isTrue();
    }

    @Test
    void rejectedAndInconclusiveAreNotDelivered() {
        assertThat(Delivery.REJECTED.delivered()).isFalse();
        assertThat(Delivery.INCONCLUSIVE.delivered()).isFalse();
    }
}
