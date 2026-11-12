package com.mcpscanner.checks.catalog.oauthconsentstructurereflection;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.scanner.audit.issues.AuditIssue;
import com.mcpscanner.checks.api.CheckDescriptor;
import com.mcpscanner.checks.api.CheckLog;
import com.mcpscanner.checks.api.HostCheck;
import com.mcpscanner.checks.shared.oauth.OAuthHttp;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.mcp.McpRequestDetector;
import com.mcpscanner.mcp.McpRequestKind;

import java.util.List;

public final class OAuthConsentStructureCheck implements HostCheck {

    private final StructureMarkers markers = new StructureMarkers();
    private final OAuthConsentStructureIssues issues =
            new OAuthConsentStructureIssues(OAuthConsentStructureDescriptor.DESCRIPTOR);
    private final CheckLog log;

    public OAuthConsentStructureCheck(McpEventLog eventLog) {
        this.log = new CheckLog(eventLog, OAuthConsentStructureDescriptor.DESCRIPTOR.id());
    }

    @Override
    public CheckDescriptor descriptor() {
        return OAuthConsentStructureDescriptor.DESCRIPTOR;
    }

    @Override
    public boolean appliesTo(HttpRequestResponse hostBaseRequestResponse) {
        return McpRequestDetector.classify(hostBaseRequestResponse) != McpRequestKind.NOT_MCP;
    }

    @Override
    public String appliesOnlyTo() {
        return "MCP requests";
    }

    @Override
    public List<AuditIssue> audit(HttpRequestResponse hostBaseRequestResponse, Http http) {
        OAuthHttp oauthHttp = new OAuthHttp(http);
        return new ConsentStructureProbe(oauthHttp, markers, new DecisionLog(log, oauthHttp))
                .run(hostBaseRequestResponse)
                .map(issues::issuesFor)
                .orElseGet(List::of);
    }
}
