package com.mcpscanner.ui;

import com.mcpscanner.auth.OAuthAuthCodeStrategy;
import com.mcpscanner.auth.oauth.AuthState;
import com.mcpscanner.auth.oauth.discovery.AuthRequirement;
import com.mcpscanner.auth.oauth.discovery.ChallengeStrategy;
import com.mcpscanner.auth.oauth.discovery.DiscoveredMetadata;
import com.mcpscanner.auth.oauth.discovery.DiscoveryFailedException;
import com.mcpscanner.auth.oauth.discovery.OAuthMetadataDiscoverer;
import com.mcpscanner.client.TransportType;
import com.mcpscanner.ui.state.ConnectionStatus;

import javax.swing.SwingUtilities;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class OAuthDiscoveryPresenter {

    public interface View {
        void onDiscoveryInFlight();

        void onDiscoverySuccess(DiscoveredMetadata metadata, List<String> selectedScopes);

        void onDiscoveryFailure();

        void onDiscoveryInvalidUrl(String detail);
    }

    private final OAuthMetadataDiscoverer discoverer;
    private final Executor workerExecutor;
    private final Executor uiExecutor;

    private final DiscoveryMemory memory = new DiscoveryMemory();
    private volatile boolean inFlight;

    public OAuthDiscoveryPresenter(OAuthMetadataDiscoverer discoverer) {
        this(discoverer, Executors.newSingleThreadExecutor(OAuthDiscoveryPresenter::daemonWorker),
                SwingUtilities::invokeLater);
    }

    OAuthDiscoveryPresenter(OAuthMetadataDiscoverer discoverer,
                            Executor workerExecutor,
                            Executor uiExecutor) {
        this.discoverer = discoverer;
        this.workerExecutor = workerExecutor;
        this.uiExecutor = uiExecutor;
    }

    private static Thread daemonWorker(Runnable task) {
        Thread worker = new Thread(task, "oauth-discovery-worker");
        worker.setDaemon(true);
        return worker;
    }

    public void shutdown() {
        if (!(workerExecutor instanceof ExecutorService ownedWorker)) {
            return;
        }
        ownedWorker.shutdownNow();
        try {
            ownedWorker.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    public boolean isDiscoveryInFlight() {
        return inFlight;
    }

    public DiscoveredMetadata discoverSync(URI mcpResource) throws DiscoveryFailedException {
        return memory.remember(mcpResource, discoverer.discover(mcpResource));
    }

    public AuthRequirement probeSync(URI mcpResource, TransportType transport) {
        AuthRequirement requirement = discoverer.requirementFor(mcpResource, strategyFor(transport));
        if (requirement instanceof AuthRequirement.OAuth oauth) {
            memory.remember(mcpResource, oauth.metadata());
        }
        return requirement;
    }

    public Optional<DiscoveredMetadata> lastDiscoveryFor(URI mcpResource) {
        return memory.recallFor(mcpResource);
    }

    private static ChallengeStrategy strategyFor(TransportType transport) {
        return switch (transport) {
            case SSE -> ChallengeStrategy.STREAM_GET;
            case AUTO -> ChallengeStrategy.POST_THEN_STREAM_GET;
            default -> ChallengeStrategy.POST;
        };
    }

    public void discoverMetadata(View view, String endpoint) {
        if (inFlight) {
            return;
        }
        URI mcpResource = parseEndpoint(endpoint);
        if (mcpResource == null) {
            uiExecutor.execute(() -> view.onDiscoveryInvalidUrl(rejectionDetail(endpoint)));
            return;
        }
        inFlight = true;
        uiExecutor.execute(view::onDiscoveryInFlight);
        workerExecutor.execute(() -> runDiscovery(view, mcpResource));
    }

    public ConnectionStatus buildConnectedStatus(OAuthAuthCodeStrategy strategy, String host, int toolCount) {
        if (strategy == null) {
            return ConnectionStatus.connected(null, host, null, toolCount);
        }
        AuthState state = strategy.snapshot();
        if (!state.valid()) {
            return ConnectionStatus.connected(state.subject(), host, null, toolCount);
        }
        long minutes = Math.max(0, Duration.between(Instant.now(), state.expiresAt()).toMinutes());
        return ConnectionStatus.connected(state.subject(), host, minutes, toolCount);
    }

    private void runDiscovery(View view, URI mcpResource) {
        try {
            DiscoveredMetadata metadata = memory.remember(mcpResource, discoverer.discover(mcpResource));
            List<String> selectedScopes = metadata.selectedScopes();
            uiExecutor.execute(() -> {
                try {
                    view.onDiscoverySuccess(metadata, selectedScopes);
                } finally {
                    inFlight = false;
                }
            });
        } catch (Exception ex) {
            uiExecutor.execute(() -> {
                try {
                    view.onDiscoveryFailure();
                } finally {
                    inFlight = false;
                }
            });
        }
    }

    private static URI parseEndpoint(String endpoint) {
        if (endpoint == null || endpoint.trim().isEmpty()) {
            return null;
        }
        try {
            URI uri = URI.create(endpoint.trim());
            if (uri.getScheme() == null || uri.getHost() == null) {
                return null;
            }
            return uri;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String rejectionDetail(String endpoint) {
        if (endpoint == null || endpoint.trim().isEmpty()) {
            return "endpoint is empty";
        }
        return "could not parse '" + endpoint.trim() + "'";
    }

}
