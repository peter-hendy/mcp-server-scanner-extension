package com.mcpscanner.testutil;

import burp.api.montoya.core.ByteArray;
import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.internal.ObjectFactoryLocator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.withSettings;

class MontoyaFactoryMemoryTest {

    @BeforeAll
    static void installFactory() {
        MontoyaTestFactory.install();
    }

    @Test
    void sharedFactoryDoesNotRecordInvocationsSoLongHarnessRunsDoNotRetainEveryExchange() {
        assertThat(mockingDetails(ObjectFactoryLocator.FACTORY).getMockCreationSettings().isStubOnly()).isTrue();
    }

    @Test
    void responseHeadersAreReleasedOnceTheResponseIsUnreachable() {
        assertCollectable(() -> HttpResponse.httpResponse("HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\n\r\nok")
                .headers().get(0));
    }

    @Test
    void headersStillRenderAsNameColonValue() {
        HttpHeader header = HttpResponse.httpResponse("HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\n\r\nok")
                .headers().get(0);

        assertThat(header).hasToString("Content-Type: text/plain");
    }

    @Test
    void byteArraysFromTextAreReleasedOnceUnreachable() {
        assertCollectable(() -> ByteArray.byteArray("payload"));
    }

    @Test
    void byteArraysFromBytesAreReleasedOnceUnreachable() {
        assertCollectable(() -> ByteArray.byteArray("payload".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void byteArraysStillRenderTheirContentAsText() {
        assertThat(ByteArray.byteArray("payload".getBytes(StandardCharsets.UTF_8))).hasToString("payload");
        assertThat(ByteArray.byteArray("payload")).hasToString("payload");
    }

    private static void moveMockitosPerThreadStateOntoAFreshMock() {
        mock(Runnable.class, withSettings().stubOnly()).run();
    }

    private static void assertCollectable(Supplier<Object> created) {
        WeakReference<Object> reference = new WeakReference<>(created.get());
        moveMockitosPerThreadStateOntoAFreshMock();

        await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(50)).until(() -> {
            System.gc();
            moveMockitosPerThreadStateOntoAFreshMock();
            return reference.get() == null;
        });
    }
}
