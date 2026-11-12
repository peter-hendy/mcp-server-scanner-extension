package com.mcpscanner.checks.catalog.downstreamrouteescape;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TimingValuesTest {

    private final TimingValues timings = new TimingValues();

    @Test
    void masksTheValueOfAFieldNamedForATime() {
        assertThat(timings.mask("{\"performance\":{\"responseTimeMs\": 64,\"uptime\":12.5,\"took\":3}}"))
                .isEqualTo("{\"performance\":{\"responseTimeMs\": 0,\"uptime\":0,\"took\":0}}");
    }

    @Test
    void masksTimingFieldsInsideEscapedToolText() {
        assertThat(timings.mask("{\\n  \\\"responseTimeMs\\\": 7,\\n  \\\"elapsed_ms\\\": 2\\n}"))
                .isEqualTo("{\\n  \\\"responseTimeMs\\\": 0,\\n  \\\"elapsed_ms\\\": 0\\n}");
    }

    @Test
    void leavesEveryOtherNumberAlone() {
        String reply = "{\"status\":404,\"items\":12,\"count\":3,\"id\":\"wf1\"}";

        assertThat(timings.mask(reply)).isEqualTo(reply);
    }
}
