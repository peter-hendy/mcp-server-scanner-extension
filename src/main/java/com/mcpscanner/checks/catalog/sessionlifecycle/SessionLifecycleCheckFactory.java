package com.mcpscanner.checks.catalog.sessionlifecycle;

import com.mcpscanner.checks.api.CheckFactory;
import com.mcpscanner.checks.api.CheckLog;
import com.mcpscanner.checks.api.CheckServices;
import com.mcpscanner.checks.api.McpCheck;
import com.mcpscanner.checks.catalog.sessionlifecycle.ids.SessionIdGuessability;
import com.mcpscanner.checks.shared.session.ProbeSessions;
import com.mcpscanner.checks.shared.stream.JdkStreamOpener;
import com.mcpscanner.checks.shared.stream.StreamOpener;

public final class SessionLifecycleCheckFactory implements CheckFactory {

    @Override
    public McpCheck create(CheckServices services) {
        return create(services, new JdkStreamOpener(services.eventLog()));
    }

    McpCheck create(CheckServices services, StreamOpener streams) {
        CheckLog log = new CheckLog(services.eventLog(), SessionLifecycleDescriptor.DESCRIPTOR.id());
        ProbeSessions probeSessions = new ProbeSessions(services.authStrategy());
        GuessabilityArm guessability = new GuessabilityArm(SessionIdGuessability.standard(), log);
        SessionLifecycleIssues issues = new SessionLifecycleIssues();
        return new SessionLifecycleCheck(new SessionLifecycleAudit(probeSessions, streams, guessability,
                new SseSessionLifecycleAudit(probeSessions, services.sseProbeStreams(), guessability, issues, log),
                issues, log));
    }
}
