package com.mcpscanner.cvetargets.burp;

import com.mcpscanner.automation.AuditSnapshot;
import com.mcpscanner.automation.ReportedIssue;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;

final class ReusedBurpLauncher implements BurpLauncher, AutoCloseable {

    private final BurpLauncher cold;
    private final int armsPerBurp;
    private WarmBurp warm;

    ReusedBurpLauncher(BurpLauncher cold, int armsPerBurp) {
        this.cold = cold;
        this.armsPerBurp = armsPerBurp;
    }

    @Override
    public synchronized BurpSession start(String label, String armOrigin, BooleanSupplier abandoned) {
        if (warm != null && warm.leased) {
            return cold.start(label, armOrigin, abandoned);
        }
        if (warm != null && !warm.canServe(armOrigin)) {
            stopWarm();
        }
        if (warm == null) {
            warm = new WarmBurp(cold.start(label, armOrigin, abandoned));
        }
        return warm.lease(armOrigin);
    }

    @Override
    public synchronized void close() {
        stopWarm();
    }

    private synchronized void release(WarmBurp leased) {
        leased.leased = false;
        if (leased == warm && !leased.endedClean()) {
            stopWarm();
        }
    }

    private void stopWarm() {
        if (warm != null) {
            WarmBurp stopping = warm;
            warm = null;
            stopping.session.close();
        }
    }

    private final class WarmBurp {
        private final BurpSession session;
        private final Set<String> origins = new HashSet<>();
        private boolean leased;

        private WarmBurp(BurpSession session) {
            this.session = session;
        }

        BurpSession lease(String armOrigin) {
            origins.add(armOrigin);
            leased = true;
            return new BurpSession() {
                @Override
                public BurpAutomation automation() {
                    return session.automation();
                }

                @Override
                public void close() {
                    release(WarmBurp.this);
                }
            };
        }

        boolean canServe(String armOrigin) {
            return origins.size() < armsPerBurp && !origins.contains(armOrigin) && snapshot().isPresent();
        }

        boolean endedClean() {
            return snapshot().filter(audit -> AuditCompletion.FINISHED.equalsIgnoreCase(audit.status()))
                    .filter(audit -> audit.issues().stream().noneMatch(ReportedIssue::extensionGenerated))
                    .isPresent();
        }

        private Optional<AuditSnapshot> snapshot() {
            try {
                return session.automation().audit();
            } catch (RuntimeException gone) {
                return Optional.empty();
            }
        }
    }
}
