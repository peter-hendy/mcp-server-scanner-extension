package com.mcpscanner.client;

import com.mcpscanner.logging.McpEventLog;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.mcpscanner.testutil.AwaitedFutures.doneWithin;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class McpClientManagerLockingTest {

    @Test
    void slowSessionTeardownDoesNotBlockOtherDisconnects() throws Exception {
        McpScannerSession session = mock(McpScannerSession.class);
        AtomicBoolean teardownStarted = new AtomicBoolean();
        CompletableFuture<Void> releaseTeardown = new CompletableFuture<>();
        AtomicBoolean firstTeardown = new AtomicBoolean(true);
        doAnswer(invocation -> {
            if (firstTeardown.getAndSet(false)) {
                teardownStarted.set(true);
                releaseTeardown.get(10, SECONDS);
            }
            return null;
        }).when(session).disconnect();
        McpClientManager manager = new McpClientManager(McpEventLog.noop(), session);

        CompletableFuture<Void> slow = CompletableFuture.runAsync(manager::disconnect);
        try {
            await().atMost(2, SECONDS).pollInterval(10, MILLISECONDS).untilTrue(teardownStarted);

            doneWithin(CompletableFuture.runAsync(manager::disconnect), Duration.ofSeconds(1)).join();
        } finally {
            releaseTeardown.complete(null);
            doneWithin(slow, Duration.ofSeconds(5)).join();
        }
    }
}
