package com.mcpscanner.access.comparison;

import burp.api.montoya.http.Http;
import com.mcpscanner.access.AccessComparison;
import com.mcpscanner.access.AccessComparisonRun;
import com.mcpscanner.auth.AuthStrategy;
import com.mcpscanner.checks.api.CheckLog;
import com.mcpscanner.checks.api.PrincipalRoster;
import com.mcpscanner.checks.api.crossidentity.PrincipalRequests;
import com.mcpscanner.logging.McpEventLog;

import java.util.function.Supplier;

public final class CrossIdentityComparison implements AccessComparison {

    static final String CHECK_ACCESS_LABEL = "Check access";

    private final McpEventLog eventLog;
    private final Supplier<AuthStrategy> primaryAuth;
    private final Supplier<PrincipalRoster> principals;
    private final CrossIdentityPorts ports;

    public CrossIdentityComparison(McpEventLog eventLog, Supplier<AuthStrategy> primaryAuth,
                                   Supplier<PrincipalRoster> principals, CrossIdentityPorts ports) {
        this.eventLog = eventLog;
        this.primaryAuth = primaryAuth;
        this.principals = principals;
        this.ports = ports;
    }

    @Override
    public AccessComparisonRun begin(Http http) {
        PrincipalRoster roster = principals.get();
        ComparisonTransport transport = new ComparisonTransport(http);
        ComparisonContext context = new ComparisonContext(new PrincipalRequests(primaryAuth, roster),
                new RunSessions(transport), transport, ports);
        return new CrossIdentityComparisonRun(roster, context, CheckLog.labelled(eventLog, CHECK_ACCESS_LABEL),
                new SameCredentialNotice(eventLog));
    }
}
