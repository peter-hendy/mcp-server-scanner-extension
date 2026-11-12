package com.mcpscanner;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.Http;
import com.mcpscanner.checks.shared.oauth.AbandonedClientLog;
import com.mcpscanner.checks.shared.oauth.ObservedRegistrationCleanup;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.scan.request.GeneratedRegistrationCleanupHandler;
import com.mcpscanner.scan.request.RegistrationCleanupWorker;

import java.time.Clock;

record RegistrationCleanupWiring(AbandonedClientLog abandonedClients,
                                 RegistrationCleanupWorker worker,
                                 ObservedRegistrationCleanup cleanup) {

    static RegistrationCleanupWiring register(MontoyaApi api, Http http, McpEventLog eventLog) {
        AbandonedClientLog abandonedClients = new AbandonedClientLog(eventLog);
        RegistrationCleanupWorker worker = new RegistrationCleanupWorker(RegistrationCleanupWorker.CAPACITY,
                RegistrationCleanupWiring::daemonThread, abandonedClients::leftByRegistration, eventLog);
        ObservedRegistrationCleanup cleanup = new ObservedRegistrationCleanup(http, Clock.systemUTC(),
                worker::runAfter, abandonedClients);
        api.http().registerHttpHandler(new GeneratedRegistrationCleanupHandler(cleanup::deleteClientRegisteredBy, worker));
        return new RegistrationCleanupWiring(abandonedClients, worker, cleanup);
    }

    private static Thread daemonThread(Runnable runnable) {
        Thread thread = new Thread(runnable, "registration-cleanup-worker");
        thread.setDaemon(true);
        return thread;
    }

    void addShutdownHooks(ShutdownHooks shutdown) {
        shutdown.add("Registration cleanup worker", worker::stop);
        shutdown.add("Retained registration cleanup", cleanup::abandonRetainedClients);
        shutdown.add("Abandoned client summary", abandonedClients::summarise);
    }
}
