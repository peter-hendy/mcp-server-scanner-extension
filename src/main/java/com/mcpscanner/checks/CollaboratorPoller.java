package com.mcpscanner.checks;

import burp.api.montoya.collaborator.Collaborator;
import burp.api.montoya.collaborator.CollaboratorClient;
import burp.api.montoya.collaborator.Interaction;
import burp.api.montoya.scanner.audit.issues.AuditIssue;
import com.mcpscanner.logging.McpEventLog;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Owns the extension's single shared {@link CollaboratorClient} and the one background poller
 * that drains its interactions, per Burp's recommended Collaborator integration model. Created
 * once, reused across every scan invocation and host.
 *
 * <p>Out-of-band checks fire their probes synchronously on the scanner thread and {@link
 * #register} a {@link ProbeContext} keyed on the minted payload id, then return immediately
 * without polling. A single-thread {@link ScheduledExecutorService} polls {@code
 * getAllInteractions()} at {@link #POLL_INTERVAL} (>= 60s — async reporting means the latency
 * never blocks the scanner), correlates each new interaction back to its context by payload id,
 * and reports the issue via the injected sink. Each interaction is reported at most once.
 *
 * <p>The poller is extension-scoped, not session-scoped: out-of-band interactions outlive the
 * session that triggered them (connect&rarr;scan&rarr;disconnect is the common flow, yet a DNS/HTTP
 * callback can land minutes later), so the context map is <em>not</em> wiped on disconnect — that
 * would silently drop a confirmed RCE finding. Instead each {@link ProbeContext} is stamped with a
 * registration time and pruned during each {@link #poll()} once it exceeds {@link #CONTEXT_TTL},
 * with a hard {@link #MAX_TRACKED_CONTEXTS} size cap (oldest evicted first) so the now-long-lived
 * map cannot grow unbounded. The same TTL bounds the {@code reportedInteractionIds} dedup set. The
 * scheduler is stopped on {@link #shutdown} (wired to extension unload); dropping any still-pending
 * contexts at unload is unavoidable and acceptable.
 */
public final class CollaboratorPoller {

    public static final Duration POLL_INTERVAL = Duration.ofSeconds(60);
    static final Duration CONTEXT_TTL = Duration.ofMinutes(15);
    static final int MAX_TRACKED_CONTEXTS = 4096;

    private final Supplier<Collaborator> collaboratorSupplier;
    private final McpEventLog eventLog;
    private final Consumer<AuditIssue> issueSink;
    private final Duration pollInterval;
    private final LongSupplier clock;

    private final AtomicReference<CollaboratorClient> sharedClient = new AtomicReference<>();
    private final Map<String, TrackedContext> contextsByPayloadId = new ConcurrentHashMap<>();
    private final Map<String, Long> reportedInteractionIds = new ConcurrentHashMap<>();
    private final AtomicReference<ScheduledExecutorService> scheduler = new AtomicReference<>();

    public CollaboratorPoller(Supplier<Collaborator> collaboratorSupplier,
                              McpEventLog eventLog,
                              Consumer<AuditIssue> issueSink) {
        this(collaboratorSupplier, eventLog, issueSink, POLL_INTERVAL, System::currentTimeMillis);
    }

    CollaboratorPoller(Supplier<Collaborator> collaboratorSupplier,
                       McpEventLog eventLog,
                       Consumer<AuditIssue> issueSink,
                       Duration pollInterval) {
        this(collaboratorSupplier, eventLog, issueSink, pollInterval, System::currentTimeMillis);
    }

    CollaboratorPoller(Supplier<Collaborator> collaboratorSupplier,
                       McpEventLog eventLog,
                       Consumer<AuditIssue> issueSink,
                       Duration pollInterval,
                       LongSupplier clock) {
        this.collaboratorSupplier = Objects.requireNonNull(collaboratorSupplier);
        this.eventLog = eventLog;
        this.issueSink = Objects.requireNonNull(issueSink);
        this.pollInterval = Objects.requireNonNull(pollInterval);
        this.clock = Objects.requireNonNull(clock);
    }

    private record TrackedContext(ProbeContext context, long registeredAtMillis) {
    }

    /**
     * @return the shared {@link CollaboratorClient}, created lazily on first use and reused
     * thereafter, or {@code null} if Collaborator is unavailable in this Burp edition.
     */
    public CollaboratorClient sharedClient() {
        CollaboratorClient existing = sharedClient.get();
        if (existing != null) {
            return existing;
        }
        CollaboratorClient created = createClient();
        if (created == null) {
            return null;
        }
        if (sharedClient.compareAndSet(null, created)) {
            return created;
        }
        return sharedClient.get();
    }

    public void register(String payloadId, ProbeContext context) {
        contextsByPayloadId.put(payloadId, new TrackedContext(context, clock.getAsLong()));
        enforceSizeCap();
    }

    /** Begins the background poll loop. Idempotent. */
    public synchronized void start() {
        if (scheduler.get() != null) {
            return;
        }
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "mcp-collaborator-poller");
            thread.setDaemon(true);
            return thread;
        });
        long millis = pollInterval.toMillis();
        executor.scheduleWithFixedDelay(this::poll, millis, millis, TimeUnit.MILLISECONDS);
        scheduler.set(executor);
    }

    public synchronized void shutdown() {
        ScheduledExecutorService executor = scheduler.getAndSet(null);
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    public boolean isShutdown() {
        ScheduledExecutorService executor = scheduler.get();
        return executor == null || executor.isShutdown();
    }

    void poll() {
        evictExpired();
        CollaboratorClient client = sharedClient.get();
        if (client == null || contextsByPayloadId.isEmpty()) {
            return;
        }
        List<Interaction> interactions;
        try {
            interactions = client.getAllInteractions();
        } catch (RuntimeException ex) {
            logInfo("tool-arg-rce: Collaborator poll failed (" + ex.getClass().getSimpleName()
                    + ": " + ex.getMessage() + ")");
            return;
        }
        if (interactions == null) {
            return;
        }
        for (Interaction interaction : interactions) {
            reportIfNew(interaction);
        }
    }

    private void reportIfNew(Interaction interaction) {
        String interactionId = interaction.id().toString();
        TrackedContext tracked = contextsByPayloadId.get(interactionId);
        if (tracked == null) {
            // Not ours yet — the probe's register() may not have committed (scanner-thread vs
            // poll-thread race). Leave the dedup id un-burned so a later poll can still emit it.
            return;
        }
        if (reportedInteractionIds.putIfAbsent(interactionId, clock.getAsLong()) != null) {
            return;
        }
        issueSink.accept(RceIssueReporter.buildIssue(tracked.context(), interaction));
    }

    /**
     * Prunes contexts and reported-interaction markers older than {@link #CONTEXT_TTL}. Driven by
     * {@link #poll()}; package-private so tests can advance an injected clock and trigger eviction
     * deterministically without waiting on wall-clock time.
     */
    void evictExpired() {
        long cutoff = clock.getAsLong() - CONTEXT_TTL.toMillis();
        contextsByPayloadId.values().removeIf(tracked -> tracked.registeredAtMillis() < cutoff);
        reportedInteractionIds.values().removeIf(reportedAt -> reportedAt < cutoff);
    }

    private void enforceSizeCap() {
        int overflow = contextsByPayloadId.size() - MAX_TRACKED_CONTEXTS;
        if (overflow <= 0) {
            return;
        }
        List<Map.Entry<String, TrackedContext>> oldestFirst =
                new ArrayList<>(contextsByPayloadId.entrySet());
        oldestFirst.sort(Comparator.comparingLong(entry -> entry.getValue().registeredAtMillis()));
        for (int i = 0; i < overflow && i < oldestFirst.size(); i++) {
            contextsByPayloadId.remove(oldestFirst.get(i).getKey(), oldestFirst.get(i).getValue());
        }
    }

    private CollaboratorClient createClient() {
        try {
            Collaborator collaborator = collaboratorSupplier.get();
            if (collaborator == null) {
                logInfo("tool-arg-rce: Collaborator unavailable, skipping (collaborator is null)");
                return null;
            }
            return collaborator.createClient();
        } catch (RuntimeException ex) {
            logInfo("tool-arg-rce: Collaborator unavailable in this Burp edition: "
                    + ex.getClass().getSimpleName());
            return null;
        }
    }

    private void logInfo(String message) {
        if (eventLog != null) {
            eventLog.info(message);
        }
    }
}
