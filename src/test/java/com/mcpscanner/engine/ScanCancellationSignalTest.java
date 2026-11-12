package com.mcpscanner.engine;

import burp.api.montoya.http.Http;
import burp.api.montoya.scanner.audit.insertionpoint.AuditInsertionPointProvider;
import burp.api.montoya.scanner.scancheck.ScanCheckType;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static com.mcpscanner.engine.EngineFixtures.LABEL;
import static com.mcpscanner.engine.EngineFixtures.answeredBase;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ScanCancellationSignalTest {

    private final AtomicReference<BooleanSupplier> cancellation = new AtomicReference<>();
    private final CountDownLatch gate = new CountDownLatch(1);
    private final ExecutorService pool = Executors.newFixedThreadPool(1);

    @BeforeEach
    void setUp() {
        MontoyaTestFactory.install();
    }

    @AfterEach
    void tearDown() {
        gate.countDown();
        pool.shutdownNow();
    }

    @Test
    void instrumentsEachScanWithItsOwnCancellation() {
        ScanInstrumentation capturing = (http, scanId, requestSent, cancelled) -> {
            cancellation.set(cancelled);
            return new UninstrumentedScan(http);
        };
        McpScanEngine engine = new McpScanEngine(mock(Http.class), mock(AuditInsertionPointProvider.class),
                new FakeSiteMap(), new FixedWorkers(pool),
                new EngineEnvironment(new McpEventLog(null), Clock.systemUTC(), capturing, request -> "named",
                        scanId -> 0));
        engine.register(FakeCheck.named("PER_REQUEST").blockingUntil(gate), ScanCheckType.PER_REQUEST);

        EngineScan scan = engine.scan(List.of(answeredBase("https://mcp.example/mcp")), LABEL);
        assertThat(cancellation.get().getAsBoolean()).isFalse();

        scan.cancel();

        assertThat(cancellation.get().getAsBoolean()).isTrue();
    }
}
