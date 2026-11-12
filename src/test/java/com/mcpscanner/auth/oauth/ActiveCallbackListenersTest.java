package com.mcpscanner.auth.oauth;

import org.junit.jupiter.api.Test;

import java.net.BindException;
import java.net.InetAddress;
import java.net.ServerSocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ActiveCallbackListenersTest {

    @Test
    void closeAllReleasesEveryListenerItStarted() throws Exception {
        ActiveCallbackListeners listeners = new ActiveCallbackListeners();
        CallbackListener first = listeners.start(0, "/callback");
        CallbackListener second = listeners.start(0, "/callback");
        int firstPort = first.port();
        int secondPort = second.port();
        assertThatThrownBy(() -> openServerSocket(firstPort)).isInstanceOf(BindException.class);
        assertThatThrownBy(() -> openServerSocket(secondPort)).isInstanceOf(BindException.class);

        listeners.closeAll();

        assertThatCode(() -> openServerSocket(firstPort).close()).doesNotThrowAnyException();
        assertThatCode(() -> openServerSocket(secondPort).close()).doesNotThrowAnyException();
    }

    @Test
    void closeAllLeavesListenersStartedByAnotherInstanceOpen() throws Exception {
        ActiveCallbackListeners mine = new ActiveCallbackListeners();
        ActiveCallbackListeners theirs = new ActiveCallbackListeners();
        try (CallbackListener other = theirs.start(0, "/callback")) {
            mine.start(0, "/callback");

            mine.closeAll();

            assertThat(other.isClosed()).isFalse();
        }
    }

    @Test
    void closeAllToleratesListenersAlreadyClosedByTheirOwner() throws Exception {
        ActiveCallbackListeners listeners = new ActiveCallbackListeners();
        CallbackListener listener = listeners.start(0, "/callback");
        int port = listener.port();
        listener.close();

        assertThatCode(listeners::closeAll).doesNotThrowAnyException();
        assertThatCode(() -> openServerSocket(port).close()).doesNotThrowAnyException();
    }

    private static ServerSocket openServerSocket(int port) throws java.io.IOException {
        return new ServerSocket(port, 0, InetAddress.getByName("127.0.0.1"));
    }
}
