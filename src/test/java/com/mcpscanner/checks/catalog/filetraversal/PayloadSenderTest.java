package com.mcpscanner.checks.catalog.filetraversal;

import burp.api.montoya.core.ByteArray;
import burp.api.montoya.http.Http;
import burp.api.montoya.http.HttpService;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.scanner.audit.insertionpoint.AuditInsertionPoint;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PayloadSenderTest {

    private final Http http = mock(Http.class);
    private final AuditInsertionPoint point = mock(AuditInsertionPoint.class);
    private final HttpRequest payloaded = mock(HttpRequest.class);

    @BeforeEach
    void stubInsertionPoint() {
        MontoyaTestFactory.install();
        when(point.buildHttpRequestWithPayload(any(ByteArray.class))).thenReturn(payloaded);
    }

    @Test
    void sendsThePayloadedRequestUnchangedWithoutAProbeService() {
        new PayloadSender(http, point, Optional::empty).send("/etc/passwd");

        verify(http).sendRequest(payloaded);
    }

    @Test
    void routesThroughTheProbeServiceWhenOneIsAvailable() {
        HttpService proxy = mock(HttpService.class);
        HttpRequest routed = mock(HttpRequest.class);
        when(payloaded.withService(proxy)).thenReturn(routed);

        new PayloadSender(http, point, () -> Optional.of(proxy)).send("/etc/passwd");

        verify(http).sendRequest(routed);
    }
}
