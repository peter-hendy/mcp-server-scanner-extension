package com.mcpscanner.checks.shared.stream;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.testutil.MontoyaTestFactory;
import com.mcpscanner.testutil.OutsideBurpRecorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class StreamOpenerTest {

    @BeforeEach
    void setUp() {
        MontoyaTestFactory.install();
    }

    @Test
    void anOpenerReportingToAnHttpReportsEveryStreamItOpensOutsideBurp() {
        OutsideBurpRecorder http = new OutsideBurpRecorder(mock(Http.class));
        StreamOpener opener = StreamOpening::unanswered;

        StreamOpening opening = opener.reportingTo(http).open(HttpRequest.httpRequestFromUrl("http://target.test/mcp"));

        assertThat(http.reported()).containsExactly(opening.exchange());
    }

    @Test
    void anOpenerReportingToAnHttpReportsNothingForAStreamItNeverSent() {
        OutsideBurpRecorder http = new OutsideBurpRecorder(mock(Http.class));
        StreamOpener opener = StreamOpening::unsent;

        opener.reportingTo(http).open(HttpRequest.httpRequestFromUrl("http://target.test/mcp"));

        assertThat(http.reported()).isEmpty();
    }
}
