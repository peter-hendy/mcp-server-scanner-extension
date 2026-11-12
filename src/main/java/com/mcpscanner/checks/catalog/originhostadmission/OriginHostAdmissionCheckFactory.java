package com.mcpscanner.checks.catalog.originhostadmission;

import com.mcpscanner.checks.api.CheckFactory;
import com.mcpscanner.checks.api.CheckServices;
import com.mcpscanner.checks.api.McpCheck;
import com.mcpscanner.checks.shared.auth.CredentialHeaders;
import com.mcpscanner.checks.shared.session.ProbeSessions;
import com.mcpscanner.checks.shared.stream.FreshSessionStreams;
import com.mcpscanner.checks.shared.stream.JdkStreamOpener;

public final class OriginHostAdmissionCheckFactory implements CheckFactory {

    @Override
    public McpCheck create(CheckServices services) {
        ProbeSessions probeSessions = new ProbeSessions(services.authStrategy());
        return new OriginHostAdmissionCheck(services.eventLog(), new CredentialHeaders(services.authStrategy()),
                services.probeService(),
                new FreshSessionStreams(probeSessions, new JdkStreamOpener(services.eventLog())), probeSessions);
    }
}
