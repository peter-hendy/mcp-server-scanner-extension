package com.mcpscanner;

import burp.api.montoya.MontoyaApi;
import com.mcpscanner.client.McpClientManager;
import com.mcpscanner.identity.credentials.IdentityCredentialServices;
import com.mcpscanner.identity.credentials.OAuthIdentitySignIn;
import com.mcpscanner.identity.credentials.SharedAuthSettings;
import com.mcpscanner.identity.credentials.ScopeApprovals;
import com.mcpscanner.identity.discovery.IdentityDiscoveryService;
import com.mcpscanner.identity.discovery.IdentityListings;

import java.net.URI;
import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class IdentityWiring {

    private final ExtensionFoundation foundation;
    private final OAuthWiring oauth;
    private final IdentityListings listings;
    private final IdentityDiscoveryService discovery;
    private final ExecutorService discoveryWorker;
    private final ExecutorService signInWorker;

    private IdentityWiring(ExtensionFoundation foundation, OAuthWiring oauth,
                           IdentityListings listings, IdentityDiscoveryService discovery,
                           ExecutorService discoveryWorker, ExecutorService signInWorker) {
        this.foundation = foundation;
        this.oauth = oauth;
        this.listings = listings;
        this.discovery = discovery;
        this.discoveryWorker = discoveryWorker;
        this.signInWorker = signInWorker;
    }

    static IdentityWiring create(MontoyaApi api, ExtensionFoundation foundation, ConnectionWiring connection,
                                 OAuthWiring oauth) {
        IdentityListings listings = new IdentityListings();
        ExecutorService worker = singleDaemonWorker("mcp-identity-discovery");
        IdentityDiscoveryService discovery = new IdentityDiscoveryService(foundation.identities(),
                connection.newIsolatedDiscovery()::discover, listings, worker, foundation.eventLog());
        discovery.start();
        McpClientManager clientManager = connection.clientManager();
        clientManager.addConnectListener(discovery::onPrimaryConnected);
        clientManager.addDisconnectListener(discovery::onDisconnected);
        foundation.configStore().addEndpointChangeListener(foundation.toolCallRefusals()::clear);
        return new IdentityWiring(foundation, oauth, listings, discovery, worker,
                singleDaemonWorker("mcp-identity-sign-in"));
    }

    IdentityListings listings() {
        return listings;
    }

    IdentityDiscoveryService discovery() {
        return discovery;
    }

    IdentityCredentialServices credentialServices(SharedAuthSettings sharedOAuthClient,
                                                  ScopeApprovals scopeApprovals) {
        OAuthIdentitySignIn signIn = new OAuthIdentitySignIn(oauth.identitySignInFlow(),
                () -> URI.create(foundation.configStore().endpoint()), oauth.discoverer()::discover, scopeApprovals,
                Clock.systemUTC(),
                foundation.eventLog(), oauth.authorizationGate()::reset);
        return IdentityCredentialServices.create(foundation.identities(), sharedOAuthClient, signIn, signInWorker,
                foundation.eventLog());
    }

    void addShutdownHooks(ShutdownHooks shutdown) {
        shutdown.add("Identity discovery", discoveryWorker::shutdownNow);
        shutdown.add("Identity sign-in", signInWorker::shutdownNow);
        shutdown.add("Identity credentials", foundation.identities()::clearCredentials);
    }

    private static ExecutorService singleDaemonWorker(String name) {
        return Executors.newSingleThreadExecutor(runnable -> daemonThread(runnable, name));
    }

    private static Thread daemonThread(Runnable runnable, String name) {
        Thread thread = new Thread(runnable, name);
        thread.setDaemon(true);
        return thread;
    }
}
