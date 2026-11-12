package com.mcpscanner.checks.catalog.unauthenticatedrequestexecution;

import com.mcpscanner.checks.api.CheckFactory;
import com.mcpscanner.checks.api.CheckServices;
import com.mcpscanner.checks.api.McpCheck;
import com.mcpscanner.checks.shared.session.ProbeSessions;
import com.mcpscanner.checks.shared.stream.FreshSessionStreams;
import com.mcpscanner.checks.shared.stream.JdkStreamOpener;

public final class UnauthenticatedRequestExecutionCheckFactory implements CheckFactory {

    @Override
    public McpCheck create(CheckServices services) {
        return new UnauthenticatedRequestExecutionCheck(services.eventLog(), services.authStrategy(), services.probeService(),
                new FreshSessionStreams(new ProbeSessions(services.authStrategy()), new JdkStreamOpener(services.eventLog())));
    }
}
