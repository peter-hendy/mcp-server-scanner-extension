package com.mcpscanner.mcp;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.List;
import java.util.Locale;

final class HttpDates {

    private static final int YEARS_BEFORE_A_TWO_DIGIT_YEAR = 49;
    private static final DateTimeFormatter ASCTIME =
            DateTimeFormatter.ofPattern("EEE MMM ppd HH:mm:ss yyyy", Locale.US).withZone(ZoneOffset.UTC);

    private HttpDates() {
    }

    static List<DateTimeFormatter> formats(Clock clock) {
        return List.of(DateTimeFormatter.RFC_1123_DATE_TIME, rfc850(clock), ASCTIME);
    }

    private static DateTimeFormatter rfc850(Clock clock) {
        LocalDate earliestYear = LocalDate.now(clock).minusYears(YEARS_BEFORE_A_TWO_DIGIT_YEAR);
        return new DateTimeFormatterBuilder()
                .appendPattern("EEEE, dd-MMM-")
                .appendValueReduced(ChronoField.YEAR, 2, 2, earliestYear)
                .appendPattern(" HH:mm:ss 'GMT'")
                .toFormatter(Locale.US)
                .withZone(ZoneOffset.UTC);
    }
}
