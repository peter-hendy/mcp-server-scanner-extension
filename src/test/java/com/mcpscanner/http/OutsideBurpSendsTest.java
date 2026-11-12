package com.mcpscanner.http;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.withSettings;

class OutsideBurpSendsTest {

    private final HttpRequestResponse exchange = mock(HttpRequestResponse.class);

    @Test
    void passesAReportThroughEveryForwardingDecoratorToTheObserverBeneath() {
        Http observer = mock(Http.class, withSettings().extraInterfaces(OutsideBurpSends.class));

        OutsideBurpSends.report(new Unchanged(new Unchanged(observer)), exchange);

        verify((OutsideBurpSends) observer).sentOutsideBurp(exchange);
    }

    @Test
    void countsAReportOnceInEveryCounterBeneathTheCheck() {
        AtomicInteger check = new AtomicInteger();
        AtomicInteger scan = new AtomicInteger();
        Http checkHttp = CountingHttp.counting(new RequestCountingHttp(mock(Http.class), scan::incrementAndGet),
                counted -> {
                    check.incrementAndGet();
                    return counted;
                });

        OutsideBurpSends.report(new Unchanged(checkHttp), exchange);

        assertThat(check).hasValue(1);
        assertThat(scan).hasValue(1);
    }

    @Test
    void ignoresAReportToAnHttpThatDoesNotObserveSendsOutsideBurp() {
        OutsideBurpSends.report(new Unchanged(mock(Http.class)), exchange);
    }

    private static final class Unchanged extends ForwardingHttp {

        Unchanged(Http delegate) {
            super(delegate);
        }

        @Override
        protected HttpRequest outgoing(HttpRequest request) {
            return request;
        }
    }
}
