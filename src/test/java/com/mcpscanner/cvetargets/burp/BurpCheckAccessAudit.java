package com.mcpscanner.cvetargets.burp;

import com.mcpscanner.automation.CheckAccessCommand;
import com.mcpscanner.automation.CheckAccessPhase;
import com.mcpscanner.automation.CheckAccessStatus;
import com.mcpscanner.cvetargets.requests.ArmEndpoint;
import com.mcpscanner.cvetargets.run.ArmAudit;
import com.mcpscanner.cvetargets.run.ArmObservation;
import com.mcpscanner.cvetargets.run.ArmProgress;
import com.mcpscanner.cvetargets.run.ArmRun;
import com.mcpscanner.cvetargets.run.ArmTeardown;
import com.mcpscanner.cvetargets.run.ProbeTally;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

public final class BurpCheckAccessAudit implements ArmAudit {

    private final BurpLauncher launcher;
    private final CheckAccessCommands commands;
    private final CheckAccessCompletion completion;
    private final Duration minimumArmTimeout;

    BurpCheckAccessAudit(BurpLauncher launcher, CheckAccessCommands commands, CheckAccessCompletion completion,
                         Duration minimumArmTimeout) {
        this.launcher = launcher;
        this.commands = commands;
        this.completion = completion;
        this.minimumArmTimeout = minimumArmTimeout;
    }

    @Override
    public String mode() {
        return "burp";
    }

    @Override
    public ArmObservation audit(ArmRun run, ArmEndpoint endpoint, ArmTeardown teardown, ArmProgress progress) {
        CheckAccessCommand command = commands.forArm(run, endpoint);
        try (BurpSession burp = launcher.start(BurpLauncher.labelFor(run), ArmOrigin.of(endpoint.uri()).value(),
                teardown::isClosed)) {
            RetriedScanAll.retried("Check access", () -> burp.automation().checkAccess(command));
            CheckAccessStatus ended = completion.await(burp.automation(), teardown::isClosed,
                    status -> progress.record(observation(status)));
            return observation(requireFinished(ended));
        }
    }

    @Override
    public Optional<String> notRunnable(ArmRun run) {
        return commands.notRunnable(run);
    }

    @Override
    public Duration armTimeout(Duration declared) {
        return declared.compareTo(minimumArmTimeout) >= 0 ? declared : minimumArmTimeout;
    }

    private static CheckAccessStatus requireFinished(CheckAccessStatus ended) {
        if (ended.status() != CheckAccessPhase.FINISHED) {
            throw new IllegalStateException("Check access in Burp " + ended.status().name().toLowerCase(
                    Locale.ROOT) + " after " + ended.checked() + " of " + ended.total() + " items ("
                    + ended.reason() + "), so it is not evidence either way");
        }
        return ended;
    }

    private static ArmObservation observation(CheckAccessStatus status) {
        return new ArmObservation(status.issues().stream().map(BurpScannerAudit::summary).toList(), 0, 0,
                new ProbeTally(status.requestsSent(), status.requestsReached()), new BurpCheckAccessDetails(status));
    }
}
