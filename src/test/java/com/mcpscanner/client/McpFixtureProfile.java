package com.mcpscanner.client;

import java.util.List;
import java.util.Optional;

public record McpFixtureProfile(boolean modern, List<String> httpVersions, String sseVersion,
                                VersionRefusal refusal, ReplyFraming framing, boolean sseVersionHeaderRequired,
                                int pingsBeforeReply, ToolPages toolPages) {

    public record ToolPages(int tools, int pageSize) {
        static final ToolPages SINGLE_TOOL = new ToolPages(1, 0);

        boolean paged() {
            return pageSize > 0;
        }
    }

    enum VersionRefusal { COUNTER_OFFER, SERVER_PREFERRED }

    enum ReplyFraming { JSON, EVENT_STREAM }

    public static McpFixtureProfile modernOnly() {
        return new McpFixtureProfile(true, List.of(), null, VersionRefusal.COUNTER_OFFER, ReplyFraming.JSON, false, 0, ToolPages.SINGLE_TOOL);
    }

    public static McpFixtureProfile streamable(String... versions) {
        return new McpFixtureProfile(false, List.of(versions), null, VersionRefusal.COUNTER_OFFER, ReplyFraming.JSON, false, 0, ToolPages.SINGLE_TOOL);
    }

    public static McpFixtureProfile sseOnly(String version) {
        return new McpFixtureProfile(false, List.of(), version, VersionRefusal.COUNTER_OFFER, ReplyFraming.JSON, false, 0, ToolPages.SINGLE_TOOL);
    }

    McpFixtureProfile withModern() {
        return new McpFixtureProfile(true, httpVersions, sseVersion, refusal, framing, sseVersionHeaderRequired, pingsBeforeReply, toolPages);
    }

    McpFixtureProfile withSse(String version) {
        return new McpFixtureProfile(modern, httpVersions, version, refusal, framing, sseVersionHeaderRequired, pingsBeforeReply, toolPages);
    }

    McpFixtureProfile withRefusal(VersionRefusal mode) {
        return new McpFixtureProfile(modern, httpVersions, sseVersion, mode, framing, sseVersionHeaderRequired, pingsBeforeReply, toolPages);
    }

    McpFixtureProfile withFraming(ReplyFraming mode) {
        return new McpFixtureProfile(modern, httpVersions, sseVersion, refusal, mode, sseVersionHeaderRequired, pingsBeforeReply, toolPages);
    }

    McpFixtureProfile requiringSseVersionHeader() {
        return new McpFixtureProfile(modern, httpVersions, sseVersion, refusal, framing, true, pingsBeforeReply, toolPages);
    }

    McpFixtureProfile pingingBeforeReplies() {
        return pingingBeforeReplies(1);
    }

    McpFixtureProfile pingingBeforeReplies(int pings) {
        return new McpFixtureProfile(modern, httpVersions, sseVersion, refusal, framing, sseVersionHeaderRequired, pings, toolPages);
    }

    public McpFixtureProfile pagingTools(int tools, int pageSize) {
        return new McpFixtureProfile(modern, httpVersions, sseVersion, refusal, framing, sseVersionHeaderRequired,
                pingsBeforeReply, new ToolPages(tools, pageSize));
    }

    boolean servesStreamableHttp() {
        return !httpVersions.isEmpty();
    }

    boolean servesSse() {
        return sseVersion != null;
    }

    Optional<String> acceptedVersion(String offered) {
        if (httpVersions.contains(offered)) {
            return Optional.of(offered);
        }
        return refusal == VersionRefusal.SERVER_PREFERRED ? Optional.of(httpVersions.get(0)) : Optional.empty();
    }

    @Override
    public String toString() {
        return (modern ? "modern+" : "") + httpVersions + (servesSse() ? "+sse:" + sseVersion : "")
                + (sseVersionHeaderRequired ? "+strict" : "") + (pingsBeforeReply > 0 ? "+pings:" + pingsBeforeReply : "") + "/" + refusal + "/" + framing;
    }
}
