package com.mcpscanner.access;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.checks.api.Principal;
import com.mcpscanner.checks.api.PrincipalRole;
import com.mcpscanner.checks.api.PrincipalRoster;
import com.mcpscanner.mcp.McpHeaders;
import com.mcpscanner.mcp.ScannerSentinels;
import com.mcpscanner.testutil.MontoyaTestFactory;
import com.mcpscanner.testutil.ImmutableHttpRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class IdentityRequestsTest {

    private static final Principal PRIMARY = Principal.primary(() -> Map.of("Authorization", "Bearer alice"));
    private static final Principal BOB = new Principal("bob", "bob", PrincipalRole.PEER,
            () -> Map.of("Authorization", "Bearer bob"), Optional.empty(), Map.of());

    private final IdentityRequests requests = new IdentityRequests(() -> PrincipalRoster.of(PRIMARY, List.of(BOB)));
    private HttpRequest primaryRequest;

    @BeforeEach
    void setUp() {
        MontoyaTestFactory.install();
        primaryRequest = ImmutableHttpRequest.post("http://mcp.test/mcp",
                Map.of("Authorization", "Bearer alice", McpHeaders.SESSION_ID, "primary-session"), "{}");
    }

    @Test
    void routedLeavesThePrimaryRequestUntouched() {
        assertThat(requests.routed(PRIMARY, primaryRequest)).isSameAs(primaryRequest);
    }

    @Test
    void routedSendsAnotherIdentityWithItsOwnCredentialsAndNoPrimarySession() {
        HttpRequest routed = requests.routed(BOB, primaryRequest);

        assertThat(routed.headerValue("Authorization")).isEqualTo("Bearer bob");
        assertThat(routed.headerValue(ScannerSentinels.IDENTITY_HEADER)).isEqualTo("bob");
        assertThat(routed.hasHeader(McpHeaders.SESSION_ID)).isFalse();
    }

    @Test
    void routedSendsAnonymousWithoutAnyCredential() {
        HttpRequest routed = requests.routed(Principal.anonymous(), primaryRequest);

        assertThat(routed.hasHeader("Authorization")).isFalse();
        assertThat(routed.headerValue(ScannerSentinels.IDENTITY_HEADER)).isEqualTo(Principal.ANONYMOUS_ID);
    }

    @Test
    void theRosterListsThePrimaryFirstThenEveryComparedIdentity() {
        assertThat(PrincipalRoster.of(PRIMARY, List.of(BOB)).all()).containsExactly(PRIMARY, BOB);
    }
}
