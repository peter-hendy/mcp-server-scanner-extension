package com.mcpscanner.automation;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class SinkServerTest {

    @Test
    void answersAnyRequestAtOnceWithAnEmptyMarkedNotFound() throws Exception {
        try (SinkServer server = SinkServer.start()) {
            String reply = exchange(server.port(), "POST /mcp?q=' HTTP/1.1\r\nHost: target\r\nContent-Length: 5\r\n\r\nhello");

            assertThat(reply).startsWith("HTTP/1.1 404");
            assertThat(reply).contains(SinkReply.HEADER + ": built-in");
            assertThat(reply).contains("Content-Length: 0");
        }
    }

    @Test
    void answersAMalformedRequestToo() throws Exception {
        try (SinkServer server = SinkServer.start()) {
            assertThat(exchange(server.port(), "GARBAGE\r\n\r\n")).startsWith("HTTP/1.1 404");
        }
    }

    @Test
    void answersARequestWithAnAbsurdContentLength() throws Exception {
        try (SinkServer server = SinkServer.start()) {
            assertThat(exchange(server.port(), "POST / HTTP/1.1\r\nContent-Length: 99999999999999999999\r\n\r\n"))
                    .startsWith("HTTP/1.1 404");
        }
    }

    @Test
    void listensOnLoopbackOnly() throws Exception {
        try (SinkServer server = SinkServer.start()) {
            assertThat(server.address().getAddress()).isEqualTo(InetAddress.getLoopbackAddress());
        }
    }

    private static String exchange(int port, String request) throws Exception {
        try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), port)) {
            socket.setSoTimeout(5000);
            OutputStream out = socket.getOutputStream();
            out.write(request.getBytes(StandardCharsets.ISO_8859_1));
            out.flush();
            InputStream in = socket.getInputStream();
            ByteArrayOutputStream reply = new ByteArrayOutputStream();
            in.transferTo(reply);
            return reply.toString(StandardCharsets.ISO_8859_1);
        }
    }
}
