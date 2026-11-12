package com.mcpscanner.scan.request;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import com.mcpscanner.checks.shared.oauth.AbandonedClientLog;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mcpscanner.testutil.Polling;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.awaitility.Awaitility.await;

class RegistrationCleanupWorkerTest {

    private static final Duration WAIT = Duration.ofSeconds(5);
    private static final String ENDPOINT = "https://auth.example.com/register";

    private final List<Thread> threads = new CopyOnWriteArrayList<>();
    private final ThreadFactory recordingThreads = runnable -> {
        Thread thread = new Thread(runnable, "test-cleanup-worker");
        thread.setDaemon(true);
        threads.add(thread);
        return thread;
    };
    private final List<String> ran = new CopyOnWriteArrayList<>();
    private final CountDownLatch release = new CountDownLatch(1);
    private final AtomicBoolean blocking = new AtomicBoolean();
    private final McpEventLog eventLog = new McpEventLog(null);
    private final RegistrationCleanupWorker worker = new RegistrationCleanupWorker(2, recordingThreads,
            new AbandonedClientLog(eventLog)::leftByRegistration, eventLog);

    @BeforeEach
    void setUp() {
        MontoyaTestFactory.install();
    }

    @AfterEach
    void tearDown() {
        release.countDown();
        worker.stop();
    }

    private static HttpRequestResponse registrationOf(String clientId) {
        String body = "{\"client_id\":\"" + clientId + "\",\"registration_client_uri\":\"" + ENDPOINT + "/"
                + clientId + "\",\"registration_access_token\":\"rat\"}";
        return HttpRequestResponse.httpRequestResponse(
                HttpRequest.httpRequestFromUrl(ENDPOINT).withMethod("POST"),
                HttpResponse.httpResponse("HTTP/1.1 201 Created\r\nContent-Type: application/json\r\n"
                        + "Content-Length: " + body.length() + "\r\n\r\n" + body));
    }

    private static HttpRequestResponse refusedRegistration() {
        return HttpRequestResponse.httpRequestResponse(
                HttpRequest.httpRequestFromUrl(ENDPOINT).withMethod("POST"),
                HttpResponse.httpResponse("HTTP/1.1 400 Bad Request\r\nContent-Length: 0\r\n\r\n"));
    }

    private static String leftBehind(String clientId, String cause) {
        return "Registered OAuth client left on https://auth.example.com (" + cause
                + "); delete it manually: registration_client_uri " + ENDPOINT + "/" + clientId
                + ", client_id " + clientId;
    }

    private List<String> loggedLines() {
        return eventLog.snapshot().stream().map(McpEventLog.LogEntry::message).toList();
    }

    private RegisteredClientCleanup recording(String name) {
        return registration -> ran.add(name);
    }

    private void occupyTheWorker() {
        worker.enqueue(refusedRegistration(), registration -> {
            blocking.set(true);
            try {
                release.await();
                ran.add("blocker");
            } catch (InterruptedException interrupted) {
                ran.add("blocker interrupted");
            }
        });
        within().untilTrue(blocking);
    }

    private void awaitWorkerExit() {
        within().until(() -> threads.stream().noneMatch(Thread::isAlive));
    }

    private static ConditionFactory within() {
        return Polling.within(WAIT);
    }

    @Test
    void runsQueuedCleanupOnItsOwnThread() throws InterruptedException {
        AtomicBoolean done = new AtomicBoolean();
        worker.enqueue(registrationOf("c1"), registration -> {
            ran.add(Thread.currentThread().getName());
            done.set(true);
        });

        within().untilTrue(done);
        assertThat(ran).containsExactly("test-cleanup-worker");
    }

    @Test
    void logsTheClientOfCleanupBeyondItsCapacityAsLeftBehind() throws InterruptedException {
        AtomicBoolean queuedRan = new AtomicBoolean();
        occupyTheWorker();
        worker.enqueue(registrationOf("c1"), registration -> {
            ran.add("queued");
            queuedRan.set(true);
        });
        worker.enqueue(registrationOf("c2"), recording("overflow"));

        release.countDown();
        within().untilTrue(queuedRan);
        assertThat(ran).containsExactly("blocker", "queued");
        assertThat(loggedLines()).containsExactly(leftBehind("c2", "cleanup queue full"));
    }

    @Test
    void stopsPromptlyLoggingTheClientOfQueuedCleanupAsLeftBehind() throws InterruptedException {
        occupyTheWorker();
        worker.enqueue(registrationOf("c1"), recording("queued"));

        worker.stop();
        awaitWorkerExit();

        assertThat(ran).containsExactly("blocker interrupted");
        assertThat(loggedLines()).containsExactly(leftBehind("c1", "cleanup discarded at unload"));
    }

    @Test
    void logsEveryClientOfCleanupArrivingOnceStoppedWithoutFillingTheQueue() {
        worker.stop();

        assertThatCode(() -> {
            worker.enqueue(registrationOf("c1"), recording("late 1"));
            worker.enqueue(registrationOf("c2"), recording("late 2"));
            worker.enqueue(registrationOf("c3"), recording("late 3"));
        }).doesNotThrowAnyException();
        assertThat(ran).isEmpty();
        assertThat(loggedLines()).containsExactly(
                leftBehind("c1", "cleanup discarded at unload"),
                leftBehind("c2", "cleanup discarded at unload"),
                leftBehind("c3", "cleanup discarded at unload"));
    }

    @Test
    void neverThrowsOnADelayedTaskOnceStopped() {
        worker.stop();

        assertThatCode(() -> worker.runAfter(Duration.ofSeconds(1), () -> ran.add("late timer")))
                .doesNotThrowAnyException();
        assertThat(ran).isEmpty();
    }

    @Test
    void runsADelayedTaskAfterItsDelay() throws InterruptedException {
        AtomicBoolean done = new AtomicBoolean();

        worker.runAfter(Duration.ofMillis(1), () -> done.set(true));

        within().untilTrue(done);
    }

    @Test
    void logsAFailedCleanupWithItsStackTraceAndReportsTheClientAsLeftBehind() {
        AtomicBoolean laterRan = new AtomicBoolean();
        RuntimeException failure = new IllegalStateException("boom");

        worker.enqueue(registrationOf("c1"), registration -> {
            throw failure;
        });
        worker.enqueue(registrationOf("c2"), registration -> laterRan.set(true));

        within().untilTrue(laterRan);
        assertThat(errorsWith(failure)).hasSize(1);
        assertThat(loggedLines()).contains(leftBehind("c1", "cleanup failed: IllegalStateException"));
    }

    @Test
    void reportsACleanupFailingAfterUnloadOnlyOnceAsDiscarded() {
        AtomicBoolean started = new AtomicBoolean();
        worker.enqueue(registrationOf("c1"), registration -> {
            started.set(true);
            try {
                release.await();
            } catch (InterruptedException interrupted) {
                throw new IllegalStateException("interrupted by unload");
            }
        });
        within().untilTrue(started);

        worker.stop();
        awaitWorkerExit();

        assertThat(loggedLines()).filteredOn(line -> line.contains("registration_client_uri " + ENDPOINT + "/c1"))
                .containsExactly(leftBehind("c1", "cleanup discarded at unload"));
    }

    @Test
    void logsAFailedDelayedTaskWithItsStackTraceAndKeepsRunningLaterOnes() {
        AtomicBoolean laterRan = new AtomicBoolean();
        RuntimeException failure = new IllegalStateException("timer boom");

        worker.runAfter(Duration.ofMillis(1), () -> {
            throw failure;
        });
        worker.runAfter(Duration.ofMillis(5), () -> laterRan.set(true));

        within().untilTrue(laterRan);
        within().until(() -> errorsWith(failure).size() == 1);
    }

    private List<McpEventLog.LogEntry> errorsWith(Throwable failure) {
        return eventLog.snapshot().stream()
                .filter(entry -> entry.level() == McpEventLog.Level.ERROR && entry.throwable() == failure)
                .toList();
    }
}
