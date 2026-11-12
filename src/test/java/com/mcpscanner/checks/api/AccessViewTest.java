package com.mcpscanner.checks.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AccessViewTest {

    @Test
    void toolCallIsKeyedByToolName() {
        assertThat(AccessItemKey.fromRequestBody(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"get_document\"}}"))
                .contains(new AccessItemKey(AccessItemKey.Kind.TOOL, "get_document"));
    }

    @Test
    void resourceReadIsKeyedByUri() {
        assertThat(AccessItemKey.fromRequestBody(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"resources/read\",\"params\":{\"uri\":\"notes://a\"}}"))
                .contains(new AccessItemKey(AccessItemKey.Kind.RESOURCE, "notes://a"));
    }

    @Test
    void otherMethodsAndMalformedBodiesHaveNoKey() {
        assertThat(AccessItemKey.fromRequestBody(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"prompts/get\",\"params\":{\"name\":\"p\"}}")).isEmpty();
        assertThat(AccessItemKey.fromRequestBody(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{}}")).isEmpty();
        assertThat(AccessItemKey.fromRequestBody("not json")).isEmpty();
    }

    @Test
    void expectationsCarryTheirDisplayLabels() {
        assertThat(AccessExpectation.ANY_SIGNED_IN.label()).isEqualTo("Any signed-in user");
        assertThat(AccessExpectation.NOT_LOWER.label()).isEqualTo("Not lower privilege");
        assertThat(AccessExpectation.OWNER_ONLY.label()).isEqualTo("Owner only");
        assertThat(AccessExpectation.HIGHER_ONLY.label()).isEqualTo("Higher privilege only");
        assertThat(AccessExpectation.SHARED_BY_DESIGN.label()).isEqualTo("Shared by design");
    }

    @Test
    void noopLedgerExpectsAnySignedInUserAndDropsOutcomes() {
        AccessLedger ledger = AccessLedger.noop();
        AccessItemKey key = new AccessItemKey(AccessItemKey.Kind.TOOL, "t");

        ledger.record(key, "bob", AccessOutcome.REFUSED);
        ledger.recordAnonymous(key, AccessOutcome.ERROR);

        assertThat(ledger.expectationFor(key)).isEqualTo(AccessExpectation.ANY_SIGNED_IN);
    }
}
