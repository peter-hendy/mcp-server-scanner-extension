package com.mcpscanner.cvetargets.burp;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KeepAliveOriginTest {

    @Test
    void answersEveryPathWithContentLengthAndKeepsTheConnectionOpen() throws Exception {
        try (KeepAliveOrigin origin = KeepAliveOrigin.start()) {
            URI immediate = URI.create(origin.immediateUrl());
            try (Socket socket = new Socket(immediate.getHost(), immediate.getPort())) {
                OutputStream out = socket.getOutputStream();
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(),
                        StandardCharsets.US_ASCII));

                List<String> first = exchange(out, in, immediate.getPath(), immediate.getPort());
                List<String> second = exchange(out, in, URI.create(origin.delayedUrl()).getPath(), immediate.getPort());

                assertThat(first).contains("HTTP/1.1 200 OK", "Content-length: 2");
                assertThat(first).noneMatch(line -> line.equalsIgnoreCase("Connection: close"));
                assertThat(second).contains("HTTP/1.1 200 OK", "Content-length: 2");
            }
        }
    }

    private static List<String> exchange(OutputStream out, BufferedReader in, String path, int port) throws Exception {
        out.write(("GET " + path + " HTTP/1.1\r\nHost: 127.0.0.1:" + port + "\r\n\r\n")
                .getBytes(StandardCharsets.US_ASCII));
        out.flush();
        List<String> head = new ArrayList<>();
        for (String line = in.readLine(); !line.isEmpty(); line = in.readLine()) {
            head.add(line);
        }
        char[] body = new char[2];
        assertThat(in.read(body)).isEqualTo(2);
        return head;
    }
}
