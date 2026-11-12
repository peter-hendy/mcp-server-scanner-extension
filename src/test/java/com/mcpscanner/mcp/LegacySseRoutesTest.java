package com.mcpscanner.mcp;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LegacySseRoutesTest {

    private final LegacySseRoutes routes = new LegacySseRoutes();

    @Test
    void aSessionIdPathSegmentSeenInTwoSessionsIsDroppedFromIssueUrls() {
        routes.observe("http://target:8080/mcp/sessions/140234");
        routes.observe("http://target:8080/mcp/sessions/140987");

        assertThat(routes.stableUrl("http://target:8080/mcp/sessions/155555"))
                .isEqualTo("http://target:8080/mcp/sessions");
    }

    @Test
    void aSingleSessionDoesNotYetShowWhichSegmentIsTheSessionId() {
        routes.observe("http://target:8080/mcp/sessions/140234");

        assertThat(routes.stableUrl("http://target:8080/mcp/sessions/140234"))
                .isEqualTo("http://target:8080/mcp/sessions/140234");
    }

    @Test
    void aFixedMessageRouteIsNeverCollapsed() {
        routes.observe("http://target:8080/message");
        routes.observe("http://target:8080/message");

        assertThat(routes.stableUrl("http://target:8080/message")).isEqualTo("http://target:8080/message");
        assertThat(routes.stableUrl("http://target:8080/other")).isEqualTo("http://target:8080/other");
    }

    @Test
    void aSessionIdQueryParameterIsDroppedKeepingTheOthers() {
        routes.observe("http://target/messages/?session_id=abc123");

        assertThat(routes.stableUrl("http://target/messages/?debug=1&session_id=def456"))
                .isEqualTo("http://target/messages/?debug=1");
        assertThat(routes.stableUrl("http://target/messages/?sessionId=def456"))
                .isEqualTo("http://target/messages/");
    }

    @Test
    void urlsOnAnotherOriginOrPathAreUnchanged() {
        routes.observe("http://target/messages/?session_id=abc123");
        routes.observe("http://target/mcp/sessions/1");
        routes.observe("http://target/mcp/sessions/2");

        assertThat(routes.stableUrl("http://other/messages/?session_id=x")).isEqualTo("http://other/messages/?session_id=x");
        assertThat(routes.stableUrl("http://target/mcp/tools/3")).isEqualTo("http://target/mcp/tools/3");
        assertThat(routes.stableUrl("not a url")).isEqualTo("not a url");
    }
}
