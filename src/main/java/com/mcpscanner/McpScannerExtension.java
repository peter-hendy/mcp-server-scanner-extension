package com.mcpscanner;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import com.mcpscanner.checks.CollaboratorPoller;
import com.mcpscanner.auth.CurrentAuthHolder;
import com.mcpscanner.auth.oauth.BrowserLauncher;
import com.mcpscanner.auth.oauth.CallbackListener;
import com.mcpscanner.auth.oauth.CallbackListenerFactory;
import com.mcpscanner.auth.oauth.OAuthAuthorizationFlow;
import com.mcpscanner.checks.issue.OAuthMetadataConsistencyReporter;
import com.mcpscanner.auth.oauth.discovery.OAuthMetadataDiscoverer;
import com.mcpscanner.auth.oauth.safety.DefaultSuspiciousDestinationGate;
import com.mcpscanner.auth.oauth.safety.SuspiciousDestinationConfirmer;
import com.mcpscanner.checks.JsonRpcDiscoveryResponseScanner;
import com.mcpscanner.checks.content.ContentFindingDedup;
import com.mcpscanner.checks.content.ContentRules;
import com.mcpscanner.checks.content.DiscoveryContentScanner;
import com.mcpscanner.checks.registry.ScanCheckRegistry;
import com.mcpscanner.checks.registry.ScanCheckSettings;
import burp.api.montoya.scanner.AuditConfiguration;
import burp.api.montoya.scanner.BuiltInAuditConfiguration;
import com.mcpscanner.client.McpClientManager;
import com.mcpscanner.proxy.McpHttpHandler;
import com.mcpscanner.proxy.SseProxyServer;
import com.mcpscanner.scan.CurrentSelectionHolder;
import com.mcpscanner.scan.JsonRpcRequestBuilder;
import com.mcpscanner.scan.McpInsertionPointProvider;
import com.mcpscanner.config.ExtensionConfigStore;
import com.mcpscanner.scan.McpScanLauncher;
import com.mcpscanner.scan.ScanStartContext;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.ui.McpScannerTab;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

import javax.swing.SwingUtilities;

public class McpScannerExtension implements BurpExtension {

    @Override
    public void initialize(MontoyaApi api) {
        api.extension().setName(ExtensionMetadata.NAME);

        ExtensionConfigStore configStore = new ExtensionConfigStore(
                api.persistence().extensionData(), api.logging());

        ScanCheckSettings checkSettings = new ScanCheckSettings(configStore);
        // One claim set shared by the connect-time DiscoveryContentScanner and the passive
        // JsonRpcDiscoveryResponseScanner so the same secret in the same discovery metadata
        // is reported once, not once per surface. Cleared on disconnect so a reconnect re-reports.
        ContentFindingDedup contentDedup = new ContentFindingDedup();
        DiscoveryContentScanner contentScanner = new DiscoveryContentScanner(
                ContentRules.all(), checkSettings, api, contentDedup);

        McpEventLog eventLog = new McpEventLog(api.logging());
        // LEGACY_PASSIVE_AUDIT_CHECKS is the only built-in passive option; Montoya does not
        // expose a per-check audit scope, so Burp's built-in passive heuristics see the
        // synthesised JSON-RPC pairs alongside our JsonRpcDiscoveryResponseScanner.
        // Discovery bodies are pure JSON envelopes with no HTML/JS/SQL patterns, so the
        // built-ins should not false-positive in practice.
        McpClientManager clientManager = new McpClientManager(
                api.logging(), contentScanner, eventLog,
                () -> api.scanner().startAudit(AuditConfiguration.auditConfiguration(
                        BuiltInAuditConfiguration.LEGACY_PASSIVE_AUDIT_CHECKS)));

        api.scanner().registerInsertionPointProvider(new McpInsertionPointProvider());

        CurrentAuthHolder authHolder = new CurrentAuthHolder();
        CurrentSelectionHolder selectionHolder = new CurrentSelectionHolder();
        // One shared CollaboratorClient and one scheduled poller across every scan invocation and
        // host, per Burp's recommended Collaborator integration model. The RCE check fires probes
        // synchronously and registers them; the poller reports confirmed issues asynchronously to
        // the site map, so the scanner thread never blocks polling Collaborator.
        CollaboratorPoller collaboratorPoller = new CollaboratorPoller(
                api::collaborator, eventLog, issue -> api.siteMap().add(issue));
        collaboratorPoller.start();
        JsonRpcDiscoveryResponseScanner discoveryResponseScanner =
                new JsonRpcDiscoveryResponseScanner(checkSettings, eventLog, contentDedup);
        ScanCheckRegistry checkRegistry = new ScanCheckRegistry(
                authHolder, checkSettings, eventLog, collaboratorPoller, selectionHolder,
                discoveryResponseScanner, clientManager.scannerSession()::transportType,
                clientManager.scannerSession()::probeBaselineService);
        checkRegistry.registerWith(api);

        McpScanLauncher scanLauncher = new McpScanLauncher(api, eventLog,
                new JsonRpcRequestBuilder(clientManager.scannerSession()::nextRequestId),
                () -> new ScanStartContext(
                        clientManager.scannerSession().resolvedEndpoint(),
                        clientManager.scannerSession().scannerHeaders()),
                checkRegistry.scanStartChecks());

        clientManager.addDisconnectListener(authHolder::clear);
        clientManager.addDisconnectListener(selectionHolder::clear);
        clientManager.addDisconnectListener(contentDedup::clear);
        // The Collaborator poller is deliberately NOT cleared on disconnect: out-of-band
        // interactions outlive the session, so wiping pending contexts here would silently drop a
        // confirmed RCE finding. It self-prunes via TTL + a size cap (see CollaboratorPoller) and
        // is stopped on extension unload. clearSessionState() below still clears the RCE check's
        // own HostDedup so a reconnect re-probes — that is the session-scoped state, kept separate.
        clientManager.addDisconnectListener(scanLauncher::clearActiveScans);
        clientManager.addDisconnectListener(checkRegistry::clearSessionState);

        DefaultSuspiciousDestinationGate authorizationGate = DefaultSuspiciousDestinationGate.withConfirmer(
                SuspiciousDestinationConfirmer.swing(
                        eventLog, () -> api.userInterface().swingUtils().suiteFrame()),
                eventLog);
        OAuthMetadataConsistencyReporter consistencyReporter = new OAuthMetadataConsistencyReporter(api, eventLog);
        OAuthAuthorizationFlow authorizationFlow = new OAuthAuthorizationFlow(
                CallbackListenerFactory.defaultFactory(),
                BrowserLauncher.validated(BrowserLauncher.desktopLauncher(eventLog), authorizationGate),
                java.time.Clock.systemUTC(),
                authorizationGate,
                eventLog,
                consistencyReporter,
                api.http());
        clientManager.addDisconnectListener(authorizationGate::reset);

        OAuthMetadataDiscoverer discoverer = OAuthMetadataDiscoverer.defaultInstance(
                api.http(), eventLog, authorizationGate, consistencyReporter);

        McpScannerTab mcpScannerTab = registerSuiteTabOnEdt(api, clientManager, scanLauncher, configStore, checkRegistry,
                checkSettings, eventLog, authHolder, selectionHolder, authorizationFlow, discoverer);

        SseProxyServer sseProxy = new SseProxyServer(clientManager.scannerSession(), api.logging());
        try {
            sseProxy.start();
            clientManager.scannerSession().setSseProxyPort(sseProxy::port);
            clientManager.addDisconnectListener(sseProxy::resetScanSession);
            api.http().registerHttpHandler(new McpHttpHandler(clientManager.scannerSession(), sseProxy));
            api.extension().registerUnloadingHandler(() -> {
                mcpScannerTab.shutdown();
                clientManager.shutdown();
                sseProxy.stop();
                collaboratorPoller.shutdown();
                eventLog.shutdown();
            });
        } catch (IOException e) {
            clientManager.setSseProxyAvailable(false);
            String message = "SSE transport unavailable: the local SSE proxy failed to start ("
                    + e.getMessage() + "). The Streamable HTTP transport is unaffected.";
            api.logging().logToError(message);
            eventLog.error(message);
            api.extension().registerUnloadingHandler(() -> {
                mcpScannerTab.shutdown();
                clientManager.shutdown();
                collaboratorPoller.shutdown();
                eventLog.shutdown();
            });
        }
        api.extension().registerUnloadingHandler(CallbackListener::closeAll);
        api.extension().registerUnloadingHandler(DefaultSuspiciousDestinationGate::shutdownSharedDnsExecutor);

        api.logging().logToOutput(ExtensionMetadata.NAME + " loaded successfully");
    }

    private static McpScannerTab registerSuiteTabOnEdt(MontoyaApi api,
                                                       McpClientManager clientManager,
                                                       McpScanLauncher scanLauncher,
                                                       ExtensionConfigStore configStore,
                                                       ScanCheckRegistry checkRegistry,
                                                       ScanCheckSettings checkSettings,
                                                       McpEventLog eventLog,
                                                       CurrentAuthHolder authHolder,
                                                       CurrentSelectionHolder selectionHolder,
                                                       OAuthAuthorizationFlow authorizationFlow,
                                                       OAuthMetadataDiscoverer discoverer) {
        McpScannerTab[] tabHolder = new McpScannerTab[1];
        try {
            SwingUtilities.invokeAndWait(() -> {
                McpScannerTab tab = new McpScannerTab(clientManager, scanLauncher, api.logging(), configStore,
                        checkRegistry, checkSettings, eventLog, authHolder, selectionHolder,
                        authorizationFlow, discoverer, api.userInterface());
                api.userInterface().registerSuiteTab(ExtensionMetadata.NAME, tab);
                tabHolder[0] = tab;
            });
        } catch (InvocationTargetException ex) {
            api.logging().logToError("Failed to register UI tab on EDT: " + ex.getCause());
            throw new IllegalStateException(ex.getCause());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
        return tabHolder[0];
    }

}
