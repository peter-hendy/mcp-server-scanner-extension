package com.mcpscanner.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RedirectModeTest {

    @Test
    void noModeIsLabelledOffBecauseEveryModeFollowsSomeRedirects() {
        assertThat(RedirectMode.values()).extracting(RedirectMode::toString).doesNotContain("Off");
    }

    @Test
    void eachModeDescribesWhatItFollows() {
        assertThat(RedirectMode.METHOD_PRESERVING_SAME_ORIGIN.description()).isEqualTo(
                "Same origin, 307/308 only: follows a 307 or 308 redirect to the same scheme, host and port, "
                        + "which keeps the method and body; other redirects are not followed.");
        assertThat(RedirectMode.SAME_ORIGIN.description()).isEqualTo(
                "Same origin, any status: follows any redirect to the same scheme, host and port.");
        assertThat(RedirectMode.ALWAYS.description()).isEqualTo(
                "Always: follows redirects to any origin, which may send credentials to a different origin.");
    }
}
