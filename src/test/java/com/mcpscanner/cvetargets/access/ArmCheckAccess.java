package com.mcpscanner.cvetargets.access;

import burp.api.montoya.http.Http;
import burp.api.montoya.scanner.audit.issues.AuditIssue;
import com.mcpscanner.access.AccessRow;
import com.mcpscanner.access.AccessRunListener;
import com.mcpscanner.access.AccessRunSummary;
import com.mcpscanner.access.AccessRuns;
import com.mcpscanner.access.CheckAccessRunner;
import com.mcpscanner.access.IdentityRequests;
import com.mcpscanner.access.OwnerRequests;
import com.mcpscanner.access.WriteReplayConfirmations;
import com.mcpscanner.access.WriteReplays;
import com.mcpscanner.access.comparison.CrossIdentityComparison;
import com.mcpscanner.access.comparison.CrossIdentityPorts;
import com.mcpscanner.checks.api.PrincipalRoster;
import com.mcpscanner.cvetargets.run.ArmRun;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;

final class ArmCheckAccess {

    private final ArmRun run;
    private final Supplier<PrincipalRoster> roster;
    private final ArmAccessRows rows;

    ArmCheckAccess(ArmRun run, Supplier<PrincipalRoster> roster, ArmAccessRows rows) {
        this.run = run;
        this.roster = roster;
        this.rows = rows;
    }

    List<AuditIssue> check(Http http) {
        List<AuditIssue> issues = new CopyOnWriteArrayList<>();
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            CompletableFuture<AccessRunSummary> finished = new CompletableFuture<>();
            runner(http, issues::add, new AccessRuns(worker))
                    .start(rows(), writes -> true, completing(finished))
                    .orElseThrow(() -> new IllegalStateException("Check access did not start"));
            finished.join();
            return List.copyOf(issues);
        } finally {
            worker.shutdownNow();
        }
    }

    private List<AccessRow> rows() {
        return rows.rows(roster.get(), run.identities().ledger());
    }

    private CheckAccessRunner runner(Http http, Consumer<AuditIssue> issues, AccessRuns runs) {
        CrossIdentityComparison comparison = new CrossIdentityComparison(run.checkLog(), run::auth, roster,
                CrossIdentityPorts.of(run.identities().ledger(), run.identities()));
        return new CheckAccessRunner(comparison, http,
                new OwnerRequests(rows::ownerRequest, new IdentityRequests(roster)), issues,
                new WriteReplays(new WriteReplayConfirmations(), run.identities().ledger()), runs, run.checkLog());
    }

    private static AccessRunListener completing(CompletableFuture<AccessRunSummary> finished) {
        return new AccessRunListener() {
            @Override
            public void progress(int done, int total) {
            }

            @Override
            public void finished(AccessRunSummary summary) {
                finished.complete(summary);
            }
        };
    }
}
