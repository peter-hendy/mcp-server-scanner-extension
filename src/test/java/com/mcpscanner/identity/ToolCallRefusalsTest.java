package com.mcpscanner.identity;

import com.mcpscanner.auth.BearerTokenAuthStrategy;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.testutil.InMemoryPersistedObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ToolCallRefusalsTest {

    static final String NEXTCRM_REFUSAL = "{\"jsonrpc\":\"2.0\",\"id\":3,\"result\":{\"content\":[{\"type\":\"text\","
            + "\"text\":\"{\\\"error\\\":\\\"Unauthorized\\\",\\\"code\\\":\\\"UNAUTHORIZED\\\"}\"}],\"isError\":true}}";

    private IdentityRegistry registry;
    private ToolCallRefusals refusals;
    private Identity userOne;

    @BeforeEach
    void setUp() {
        registry = new IdentityRegistry(new IdentityStore(InMemoryPersistedObject.configStore(), McpEventLog.noop()));
        refusals = new ToolCallRefusals(registry);
        userOne = registry.primaryUser();
    }

    @Test
    void nothingIsRefusedUntilAToolCallIsRefused() {
        assertThat(refusals.refusedWithoutCredentials()).isFalse();
    }

    @Test
    void eachRefusedToolCallWhileThePrimaryUserHasNoCredentialIsCounted() {
        AtomicInteger changes = new AtomicInteger();
        refusals.addListener(changes::incrementAndGet);

        refusals.toolCallRefused();
        refusals.toolCallRefused();

        assertThat(refusals.refusedWithoutCredentials()).isTrue();
        assertThat(refusals.refusedCount()).isEqualTo(2);
        assertThat(changes).hasValue(2);
    }

    @Test
    void clearingForgetsTheCount() {
        refusals.toolCallRefused();

        refusals.clear();

        assertThat(refusals.refusedCount()).isZero();
        assertThat(refusals.refusedWithoutCredentials()).isFalse();
    }

    @Test
    void aRefusalWhileTheConnectAsUserHoldsACredentialIsNotAMissingCredential() {
        registry.setCredential(userOne.id(), new BearerTokenAuthStrategy("token"));

        refusals.toolCallRefused();

        assertThat(refusals.refusedWithoutCredentials()).isFalse();
    }

    @Test
    void theSignalClearsOnceTheUserGetsACredentialOrAnotherUserConnects() {
        Identity bob = registry.add(Identity.create("bob", IdentityRole.PEER, IdentityAuthKind.NONE));
        refusals.toolCallRefused();

        registry.setCredential(userOne.id(), new BearerTokenAuthStrategy("token"));
        assertThat(refusals.refusedWithoutCredentials()).isFalse();

        registry.removeCredential(userOne.id());
        refusals.toolCallRefused();
        registry.makePrimary(bob.id());
        assertThat(refusals.refusedWithoutCredentials()).isFalse();
    }

    @Test
    void aRefusingToolCallReplyRaisesTheSignal() {
        refusals.replied(200, NEXTCRM_REFUSAL);

        assertThat(refusals.refusedWithoutCredentials()).isTrue();
    }

    @Test
    void anAnsweredToolCallReplyLeavesTheSignalDown() {
        refusals.replied(200, "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"content\":[{\"type\":\"text\","
                + "\"text\":\"campaign 7\"}]}}");

        assertThat(refusals.refusedWithoutCredentials()).isFalse();
    }

    @Test
    void listenersCanUnsubscribe() {
        AtomicInteger changes = new AtomicInteger();
        Runnable listener = changes::incrementAndGet;
        refusals.addListener(listener);

        refusals.removeListener(listener);
        refusals.toolCallRefused();

        assertThat(changes).hasValue(0);
    }

    @Test
    void clearingForgetsTheRefusal() {
        refusals.toolCallRefused();

        refusals.clear();

        assertThat(refusals.refusedWithoutCredentials()).isFalse();
    }
}
