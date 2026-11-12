package com.mcpscanner.mcp;

import burp.api.montoya.http.message.responses.HttpResponse;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class RetryAfterTest {

    private static final Clock NOW = Clock.fixed(Instant.parse("2026-10-21T07:27:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        MontoyaTestFactory.install();
    }

    private static Optional<Duration> retryAfter(String value) {
        return RetryAfter.of(HttpResponse.httpResponse("HTTP/1.1 429 \r\nRetry-After: " + value + "\r\n\r\n"), NOW);
    }

    @Test
    void readsDelaySeconds() {
        assertThat(retryAfter("30")).contains(Duration.ofSeconds(30));
    }

    @Test
    void readsAFutureHttpDateAsTheTimeUntilIt() {
        assertThat(retryAfter("Wed, 21 Oct 2026 07:28:00 GMT")).contains(Duration.ofSeconds(60));
    }

    @Test
    void readsAnObsoleteRfc850DateAsTheTimeUntilIt() {
        assertThat(retryAfter("Wednesday, 21-Oct-26 07:28:00 GMT")).contains(Duration.ofSeconds(60));
    }

    @Test
    void readsAnObsoleteAsctimeDateAsTheTimeUntilIt() {
        assertThat(retryAfter("Wed Oct 21 07:28:00 2026")).contains(Duration.ofSeconds(60));
    }

    @Test
    void readsAnAsctimeDateWithASpacePaddedDay() {
        assertThat(retryAfter("Wed Oct  7 07:28:00 2026")).contains(Duration.ZERO);
    }

    @Test
    void readsAnRfc850YearMoreThanFiftyYearsAheadAsTheLastCentury() {
        assertThat(retryAfter("Sunday, 06-Nov-94 08:49:37 GMT")).contains(Duration.ZERO);
    }

    @Test
    void readsAPastHttpDateAsNoWait() {
        assertThat(retryAfter("Wed, 21 Oct 2026 07:20:00 GMT")).contains(Duration.ZERO);
    }

    @Test
    void isAbsentWithoutTheHeader() {
        assertThat(RetryAfter.of(HttpResponse.httpResponse("HTTP/1.1 429 \r\n\r\n"), NOW)).isEmpty();
    }

    @Test
    void ignoresAValueThatIsNeitherSecondsNorADate() {
        assertThat(retryAfter("soon")).isEmpty();
        assertThat(retryAfter("-5")).isEmpty();
        assertThat(retryAfter("1.5")).isEmpty();
    }

    @Test
    void saturatesASecondsValueTooLargeForALong() {
        assertThat(retryAfter("99999999999999999999")).contains(Duration.ofSeconds(Long.MAX_VALUE));
    }
}
