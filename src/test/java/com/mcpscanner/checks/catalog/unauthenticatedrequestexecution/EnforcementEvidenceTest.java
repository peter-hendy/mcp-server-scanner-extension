package com.mcpscanner.checks.catalog.unauthenticatedrequestexecution;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.auth.BearerTokenAuthStrategy;
import com.mcpscanner.checks.shared.auth.AuthVariantSuite;
import com.mcpscanner.checks.shared.auth.oracle.BaselineEquivalenceOracle;
import com.mcpscanner.checks.shared.probe.ProbeChannel;
import com.mcpscanner.checks.shared.probe.ProbeExchange;
import com.mcpscanner.checks.shared.probe.SimulatedMcpServer;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EnforcementEvidenceTest {

    private static final Map<String, String> AUTHENTICATED = Map.of("Authorization", SimulatedMcpServer.VALID_TOKEN);

    private final BaselineEquivalenceOracle oracle = BaselineEquivalenceOracle.defaultOracle();
    private final EnforcementEvidence evidence = new EnforcementEvidence(oracle);

    @BeforeEach
    void installMontoya() {
        MontoyaTestFactory.install();
    }

    @Test
    void aRefusalOfARequestThatAlsoFailedWithCredentialsIsNotEnforcement() {
        SimulatedMcpServer enforcing = SimulatedMcpServer.enforcing();
        SimulatedMcpServer open = SimulatedMcpServer.open();
        HttpRequestResponse failedBaseline = enforcing.exchange(
                SimulatedMcpServer.request(SimulatedMcpServer.toolCall(), Map.of()));
        AuditSubject failing = new AuditSubject(failedBaseline,
                SeedMessage.of(failedBaseline.request()).orElseThrow(), true);
        evidence.recordRefusal(failing, ProbeExchange.of(failedBaseline));

        assertThat(evidence.find(subject(open), probe(open.http()))).isEmpty();
    }

    @Test
    void aConcurrentAuditWaitsForTheHostProbeInsteadOfReportingNoEvidence() throws Exception {
        SimulatedMcpServer server = SimulatedMcpServer.open().challengingAnonymousPing();
        AtomicBoolean probing = new AtomicBoolean();
        CountDownLatch release = new CountDownLatch(1);
        Http blockingPing = mock(Http.class);
        when(blockingPing.sendRequest(any(HttpRequest.class))).thenAnswer(invocation -> {
            HttpRequest request = invocation.getArgument(0);
            if (request.bodyToString().contains("\"ping\"")) {
                probing.set(true);
                release.await(5, TimeUnit.SECONDS);
            }
            return server.exchange(request);
        });
        AuditSubject subject = subject(server);

        CompletableFuture<Optional<HttpRequestResponse>> first =
                CompletableFuture.supplyAsync(() -> evidence.find(subject, probe(blockingPing)));
        await().atMost(Duration.ofSeconds(5)).untilTrue(probing);
        Thread second = new Thread(() -> assertThat(evidence.find(subject, probe(blockingPing))).isPresent());
        CompletableFuture<Void> secondDone = new CompletableFuture<>();
        second.setUncaughtExceptionHandler((thread, failure) -> secondDone.completeExceptionally(failure));
        second.start();
        await().atMost(Duration.ofSeconds(5)).until(() -> second.getState() == Thread.State.WAITING);
        release.countDown();
        await().atMost(Duration.ofSeconds(5)).until(() -> !second.isAlive());
        secondDone.complete(null);

        await().atMost(Duration.ofSeconds(5)).until(first::isDone);
        assertThat(first.join()).isPresent();
        assertThat(secondDone).isCompleted().isNotCompletedExceptionally();
    }

    private AuditSubject subject(SimulatedMcpServer server) {
        HttpRequestResponse baseline = server.exchange(
                SimulatedMcpServer.request(SimulatedMcpServer.toolCall(), AUTHENTICATED));
        return new AuditSubject(baseline, SeedMessage.of(baseline.request()).orElseThrow(), true);
    }

    private static EnforcementProbe probe(Http http) {
        return new EnforcementProbe(new ProbeChannel(http, 10), new AuthVariantSuite(
                () -> new BearerTokenAuthStrategy("valid-token"), "canary"));
    }
}
