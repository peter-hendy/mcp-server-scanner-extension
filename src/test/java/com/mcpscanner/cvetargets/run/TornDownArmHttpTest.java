package com.mcpscanner.cvetargets.run;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TornDownArmHttpTest {

    private final Http delegate = mock(Http.class);
    private final HttpRequest request = mock(HttpRequest.class);
    private final ArmTeardown teardown = new ArmTeardown();

    @Test
    void sendsWhileTheArmIsUp() {
        HttpRequestResponse exchange = mock(HttpRequestResponse.class);
        when(delegate.sendRequest(request)).thenReturn(exchange);

        assertThat(new TornDownArmHttp(delegate, teardown).sendRequest(request)).isSameAs(exchange);
    }

    @Test
    void refusesEveryProbeOnceTheArmIsTornDown() {
        teardown.close();

        assertThatThrownBy(() -> new TornDownArmHttp(delegate, teardown).sendRequest(request))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("torn down");
        verifyNoInteractions(delegate);
    }
}
