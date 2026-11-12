package com.mcpscanner.ui.scans;

import com.mcpscanner.mcp.HttpOrigin;

import java.util.Optional;
import java.util.Set;

record IssueHost(Set<String> origins, String host) {

    static Optional<IssueHost> of(String url) {
        return HttpOrigin.parse(url).map(origin -> new IssueHost(labelsOf(origin), origin.host()));
    }

    boolean isOrigin(String label) {
        return origins.contains(label);
    }

    boolean isBareHost(String label) {
        return host.equals(label);
    }

    private static Set<String> labelsOf(HttpOrigin origin) {
        boolean defaultPort = origin.port() == HttpOrigin.defaultPort(origin.scheme());
        return defaultPort
                ? Set.of(origin.baseUrl(), origin.baseUrl() + ":" + origin.port())
                : Set.of(origin.baseUrl(), origin.hostHeader());
    }
}
