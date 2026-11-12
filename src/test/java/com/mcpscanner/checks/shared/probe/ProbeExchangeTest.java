package com.mcpscanner.checks.shared.probe;

import burp.api.montoya.http.message.HttpRequestResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class ProbeExchangeTest {

    @Test
    void lastIsTheFinalExchange() {
        HttpRequestResponse first = mock(HttpRequestResponse.class);
        HttpRequestResponse second = mock(HttpRequestResponse.class);

        assertThat(ProbeExchange.of(first, second).last()).isSameAs(second);
        assertThat(ProbeExchange.of(first).exchanges()).containsExactly(first);
    }

    @Test
    void anEmptyExchangeIsRejected() {
        assertThatThrownBy(() -> new ProbeExchange(List.of())).isInstanceOf(IllegalArgumentException.class);
    }
}
