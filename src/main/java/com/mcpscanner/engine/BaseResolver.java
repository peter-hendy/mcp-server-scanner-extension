package com.mcpscanner.engine;

import burp.api.montoya.http.message.HttpRequestResponse;

import java.util.Optional;

final class BaseResolver {

    private final EngineLog log;

    BaseResolver(EngineLog log) {
        this.log = log;
    }

    Optional<HttpRequestResponse> resolve(ScanRun run, HttpRequestResponse base) {
        return base.hasResponse() ? Optional.of(base) : send(run, base);
    }

    private Optional<HttpRequestResponse> send(ScanRun run, HttpRequestResponse base) {
        try {
            HttpRequestResponse sent = run.instrumented().http().sendRequest(base.request());
            if (sent != null && sent.hasResponse()) {
                return Optional.of(sent);
            }
            skip(run, base, "no response to the base request");
        } catch (RuntimeException failure) {
            skip(run, base, FailureDescription.of(failure));
        }
        return Optional.empty();
    }

    private void skip(ScanRun run, HttpRequestResponse base, String reason) {
        String url = base.request().url();
        log.warn("skipped " + url + ": " + reason);
        run.scan().failed(Optional.empty(), url, Optional.empty(), reason);
    }
}
