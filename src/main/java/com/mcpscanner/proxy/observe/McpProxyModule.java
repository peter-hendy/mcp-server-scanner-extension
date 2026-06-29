package com.mcpscanner.proxy.observe;

import burp.api.montoya.MontoyaApi;
import com.mcpscanner.client.McpScannerSession;
import com.mcpscanner.proxy.observe.preflight.ProxyPreflight;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Single integration point for the MCP-proxy live-observation feature (Phase 1, Path A observation).
 * Constructs and exposes the feature's runtime collaborators, all gated by the {@code mcpProxyEnabled}
 * toggle so the feature is OFF by default and runtime-flippable.
 *
 * <p>What the module wires together:
 * <ul>
 *   <li>a shared {@link McpExchangeLog} fed by the injected {@link PassiveLiveRunner} (in production
 *       the real live content scanner), with an append listener that pushes each appended row to the
 *       injected {@code tabFeed};</li>
 *   <li>a toggle-aware {@link SwapPolicy} ({@link ToggleAwareSwapPolicy}): OFF → swap every matching
 *       tool (today's behaviour, reads no {@code toolSource}); ON → narrow to the scanner family;</li>
 *   <li>a toggle-aware {@link BurpTrafficObserver} ({@link ToggleAwareBurpTrafficObserver}): OFF →
 *       no-op; ON → a real {@link BurpObserverAdapter} tapping un-swapped live MCP traffic into the
 *       log;</li>
 *   <li>the {@link ProxyPreflight} the UI renders so the operator sees what to verify manually.</li>
 * </ul>
 *
 * <h2>Decoupling / EDT contract</h2>
 * The module takes the tab feed as a plain {@link Consumer Consumer&lt;McpExchange&gt;} and the
 * passive scanner as the {@link PassiveLiveRunner} interface — NOT the concrete {@code ui} /
 * {@code checks.content} types — so this package never imports {@code ui} or {@code checks}, keeping
 * the top-level package graph acyclic (see {@code CycleFreedomTest}). The composition root supplies
 * the concrete collaborators. The {@code tabFeed} consumer is invoked on the observing (live proxy /
 * scanner) thread; the root is responsible for marshalling onto the Swing EDT via
 * {@code SwingUtilities.invokeLater(...)} before touching {@code TrafficTablePanel}, per that panel's
 * documented EDT contract.
 *
 * <h2>REMOVAL PROCEDURE</h2>
 * The whole feature is a bounded, shielded addition. {@code ProxyShieldArchTest} guarantees nothing
 * outside the allowlisted seams references {@code proxy.observe} / {@code proxy.live}, so removal is:
 * <ol>
 *   <li>delete packages {@code com.mcpscanner.proxy.observe} and {@code com.mcpscanner.proxy.live}
 *       (the latter is Phase-2 and may not yet exist);</li>
 *   <li>delete {@code ui/TrafficTablePanel} + {@code ui/TrafficTableModel} and remove the "Traffic"
 *       sub-tab wiring in {@code McpScannerTab};</li>
 *   <li>delete {@code checks/content/LiveContentPassiveRunner};</li>
 *   <li>revert the {@code ContentFindingDedup} {@link ExposureSurface} overloads back to the
 *       single-namespace (discovery-only) form;</li>
 *   <li>revert {@code McpHttpHandler} to its pre-feature 2-arg shape: drop the {@code SwapPolicy}
 *       and {@code BurpTrafficObserver} fields/constructor params, both {@code proxy.observe} imports,
 *       and the {@code observer.observeRequest(...)} / {@code observer.observeResponse(...)} calls;
 *       restore the unconditional {@code if (target != null)} service-swap in
 *       {@code handleHttpRequestToBeSent} and the bare {@code handleHttpResponseReceived}
 *       pass-through. Then revert the fenced block in {@code McpScannerExtension} back to
 *       {@code new McpHttpHandler(clientManager.scannerSession(), sseProxy)} and remove the
 *       Traffic-tab feed registration, the disconnect clear of the log, and the
 *       {@code mcpProxyEnabled} toggle UI.</li>
 * </ol>
 *
 * <p>Two additive, dependency-free helpers introduced by this feature may optionally be cleaned up
 * but are harmless to leave in place: {@code McpRequestDetector.responseContentKindForMethod(...)}
 * and {@code ResponseBodyContentExtractor.extractFromResult(...)}. The latter is now also used by
 * the existing {@code ResponseBodyContentExtractor.extract(...)} path, so removing it requires
 * inlining it back into that caller (optional).
 */
public final class McpProxyModule {

    private final ProxyPreflight preflight;
    private final McpExchangeLog exchangeLog;
    private final SwapPolicy swapPolicy;
    private final BurpTrafficObserver trafficObserver;

    public McpProxyModule(MontoyaApi api,
                          BooleanSupplier proxyEnabled,
                          McpScannerSession scannerSession,
                          PassiveLiveRunner passiveRunner,
                          Consumer<McpExchange> tabFeed) {
        Objects.requireNonNull(api, "api must not be null");
        Objects.requireNonNull(proxyEnabled, "proxyEnabled must not be null");
        Objects.requireNonNull(scannerSession, "scannerSession must not be null");
        Objects.requireNonNull(passiveRunner, "passiveRunner must not be null");
        Objects.requireNonNull(tabFeed, "tabFeed must not be null");

        this.preflight = new ProxyPreflight(api);
        this.exchangeLog = new McpExchangeLog(passiveRunner);
        this.exchangeLog.setAppendListener(tabFeed);
        this.swapPolicy = new ToggleAwareSwapPolicy(proxyEnabled);
        this.trafficObserver = new ToggleAwareBurpTrafficObserver(
                proxyEnabled, new BurpObserverAdapter(exchangeLog, scannerSession));
    }

    public SwapPolicy swapPolicy() {
        return swapPolicy;
    }

    public BurpTrafficObserver trafficObserver() {
        return trafficObserver;
    }

    public ProxyPreflight preflight() {
        return preflight;
    }

    /** The shared exchange log — exposed so the composition root can clear it on disconnect. */
    public McpExchangeLog exchangeLog() {
        return exchangeLog;
    }
}
