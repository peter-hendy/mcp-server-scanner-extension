package com.mcpscanner.engine;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.scanner.audit.issues.AuditIssue;
import burp.api.montoya.sitemap.SiteMap;
import burp.api.montoya.sitemap.SiteMapFilter;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class FakeSiteMap implements SiteMap {

    private final List<AuditIssue> held = new CopyOnWriteArrayList<>();
    private final List<AuditIssue> added = new CopyOnWriteArrayList<>();

    static FakeSiteMap holding(AuditIssue... existing) {
        FakeSiteMap siteMap = new FakeSiteMap();
        siteMap.held.addAll(List.of(existing));
        return siteMap;
    }

    List<AuditIssue> added() {
        return List.copyOf(added);
    }

    void delete(AuditIssue issue) {
        held.remove(issue);
    }

    @Override
    public List<AuditIssue> issues(SiteMapFilter filter) {
        return List.copyOf(held);
    }

    @Override
    public List<AuditIssue> issues() {
        return List.copyOf(held);
    }

    @Override
    public void add(AuditIssue issue) {
        held.add(issue);
        added.add(issue);
    }

    @Override
    public List<HttpRequestResponse> requestResponses(SiteMapFilter filter) {
        return List.of();
    }

    @Override
    public List<HttpRequestResponse> requestResponses() {
        return List.of();
    }

    @Override
    public void add(HttpRequestResponse requestResponse) {
        throw new UnsupportedOperationException("the engine adds only issues");
    }
}
