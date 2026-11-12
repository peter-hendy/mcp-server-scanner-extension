package com.mcpscanner.cvetargets.manifest;

import com.mcpscanner.auth.AuthStrategy;
import com.mcpscanner.auth.grant.RefreshGrantStore;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

record HeaderAddingStrategy(AuthStrategy credential, Map<String, String> extraHeaders) implements AuthStrategy {

    @Override
    public Map<String, String> headers() {
        Map<String, String> all = new LinkedHashMap<>(extraHeaders);
        all.putAll(credential.headers());
        return all;
    }

    @Override
    public boolean supportsRefresh() {
        return credential.supportsRefresh();
    }

    @Override
    public boolean refresh() {
        return credential.refresh();
    }

    @Override
    public boolean isTerminallyFailed() {
        return credential.isTerminallyFailed();
    }

    @Override
    public Optional<RefreshGrantStore> refreshGrantStore() {
        return credential.refreshGrantStore();
    }
}
