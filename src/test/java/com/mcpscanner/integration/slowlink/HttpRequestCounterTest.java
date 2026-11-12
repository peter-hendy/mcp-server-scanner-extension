package com.mcpscanner.integration.slowlink;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpRequestCounterTest {

    private static final String POST = "POST /mcp HTTP/1.1\r\nHost: a\r\nContent-Length: 30\r\n\r\n"
            + "GET /trap HTTP/1.1\r\n\r\n1234567\r\n".substring(0, 30);
    private static final String GET = "GET /mcp HTTP/1.1\r\nHost: a\r\n\r\n";

    private final AtomicInteger requests = new AtomicInteger();
    private final HttpRequestCounter counter = new HttpRequestCounter(requests::incrementAndGet);

    @Test
    void countsPipelinedRequestsOnOneConnectionWithoutCountingBodies() {
        feed(POST + GET + POST);

        assertThat(requests).hasValue(3);
    }

    @Test
    void countsARequestSplitAcrossManyChunks() {
        (POST + GET).chars().forEach(character -> feed(String.valueOf((char) character)));

        assertThat(requests).hasValue(2);
    }

    @Test
    void ignoresBlankLinesBetweenRequests() {
        feed(GET + "\r\n" + GET);

        assertThat(requests).hasValue(2);
    }

    @Test
    void refusesAChunkedRequestBodyItCannotFrame() {
        assertThatThrownBy(() -> feed("POST /mcp HTTP/1.1\r\nTransfer-Encoding: chunked\r\n\r\n0\r\n\r\n"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Transfer-Encoding");
    }

    private void feed(String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        counter.observe(bytes, bytes.length);
    }
}
