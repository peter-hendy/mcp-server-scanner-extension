package com.mcpscanner.checks.shared.session;

import com.mcpscanner.core.connection.HttpProbeSession;

public final class SessionCleanup {

    private SessionCleanup() {
    }

    public static void delete(HttpProbeSession session) {
        boolean interrupted = Thread.interrupted();
        try {
            session.close();
        } catch (RuntimeException alreadyGone) {
            return;
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
