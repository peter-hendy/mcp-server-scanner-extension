package com.mcpscanner.client;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class ConnectionSetupResendTest {

    private static final ProtocolHttpRequest REQUEST =
            new ProtocolHttpRequest(URI.create("http://127.0.0.1:1/mcp"), "POST", Map.of(), "{}");
    private static final Duration TIMEOUT = Duration.ofSeconds(1);

    private final ProtocolHttpTransport delegate = mock(ProtocolHttpTransport.class);
    private final ProtocolHttpTransport transport = StaleConnectionResendingTransport.always(delegate);

    @Test
    void aRequestSentOnAConnectionTheServerAlreadyClosedIsSentOnceMore() throws Exception {
        ProtocolHttpResponse answered = answered();
        doThrow(new IOException("HTTP/1.1 header parser received no bytes")).doReturn(answered)
                .when(delegate).send(any(), any());

        assertThat(transport.send(REQUEST, TIMEOUT)).isSameAs(answered);
        verify(delegate, times(2)).send(REQUEST, TIMEOUT);
    }

    @Test
    void aSecondClosedConnectionIsReported() throws Exception {
        doThrow(new IOException("header parser received no bytes")).when(delegate).send(any(), any());

        assertThatThrownBy(() -> transport.send(REQUEST, TIMEOUT)).hasMessageContaining("no bytes");
        verify(delegate, times(2)).send(REQUEST, TIMEOUT);
    }

    @Test
    void otherTransportFailuresAreNotResent() throws Exception {
        doThrow(new IOException("Connection refused")).when(delegate).send(any(), any());

        assertThatThrownBy(() -> transport.send(REQUEST, TIMEOUT)).hasMessageContaining("refused");
        verify(delegate, times(1)).send(REQUEST, TIMEOUT);
    }

    @Test
    void aTimeoutIsNotResentEvenWhenItsMessageMentionsTheParser() throws Exception {
        doThrow(new HttpTimeoutException("header parser received no bytes")).when(delegate).send(any(), any());

        assertThatThrownBy(() -> transport.send(REQUEST, TIMEOUT)).isInstanceOf(HttpTimeoutException.class);
        verify(delegate, times(1)).send(REQUEST, TIMEOUT);
    }

    @Test
    void aRequestTheCallerDoesNotAllowToBeResentIsNotResent() throws Exception {
        doThrow(new IOException("header parser received no bytes")).when(delegate).send(any(), any());
        ProtocolHttpTransport guarded = StaleConnectionResendingTransport.when(delegate, request -> false);

        assertThatThrownBy(() -> guarded.send(REQUEST, TIMEOUT)).hasMessageContaining("no bytes");
        verify(delegate, times(1)).send(REQUEST, TIMEOUT);
    }

    private static ProtocolHttpResponse answered() {
        return new ProtocolHttpResponse(200, Map.of(), InputStream.nullInputStream(), REQUEST.uri());
    }
}
