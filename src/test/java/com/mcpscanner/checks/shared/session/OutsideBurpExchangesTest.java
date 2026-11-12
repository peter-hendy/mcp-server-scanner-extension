package com.mcpscanner.checks.shared.session;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.message.HttpRequestResponse;
import com.mcpscanner.http.OutsideBurpSends;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.withSettings;

class OutsideBurpExchangesTest {

    @Test
    void holdsExactlyTheExchangesReportedOutsideBurp() {
        HttpRequestResponse reported = mock(HttpRequestResponse.class);
        OutsideBurpExchanges exchanges = new OutsideBurpExchanges(mock(Http.class));

        OutsideBurpSends.report(exchanges, reported);

        assertThat(exchanges.sent()).singleElement().isSameAs(reported);
        assertThat(exchanges.sent().contains(mock(HttpRequestResponse.class))).isFalse();
    }

    @Test
    void passesEveryReportOnToTheHttpItWraps() {
        Http observer = mock(Http.class, withSettings().extraInterfaces(OutsideBurpSends.class));
        HttpRequestResponse reported = mock(HttpRequestResponse.class);

        OutsideBurpSends.report(new OutsideBurpExchanges(observer), reported);

        verify((OutsideBurpSends) observer).sentOutsideBurp(reported);
    }
}
