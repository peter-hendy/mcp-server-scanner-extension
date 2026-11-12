package com.mcpscanner.cvetargets.burp;

import com.mcpscanner.automation.AuditSnapshot;
import com.mcpscanner.automation.CheckAccessCommand;
import com.mcpscanner.automation.CheckAccessPhase;
import com.mcpscanner.automation.CheckAccessStatus;
import com.mcpscanner.automation.LaunchedScan;
import com.mcpscanner.automation.ScanAllCommand;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

final class FakeBurpAutomation implements BurpAutomation {

    private final Supplier<Optional<CheckAccessStatus>> statuses;
    private final List<CheckAccessCommand> launched = new CopyOnWriteArrayList<>();
    private final int failedLaunches;

    FakeBurpAutomation(Supplier<Optional<CheckAccessStatus>> statuses) {
        this(statuses, 0);
    }

    FakeBurpAutomation(Supplier<Optional<CheckAccessStatus>> statuses, int failedLaunches) {
        this.statuses = statuses;
        this.failedLaunches = failedLaunches;
    }

    List<CheckAccessCommand> launched() {
        return List.copyOf(launched);
    }

    @Override
    public LaunchedScan scanAll(ScanAllCommand command) {
        throw new UnsupportedOperationException("Check access arms never scan");
    }

    @Override
    public Optional<AuditSnapshot> audit() {
        return Optional.empty();
    }

    @Override
    public CheckAccessStatus checkAccess(CheckAccessCommand command) {
        launched.add(command);
        if (launched.size() <= failedLaunches) {
            throw new IllegalStateException("attempt " + launched.size() + ": Failed to establish scanner session");
        }
        return new CheckAccessStatus(CheckAccessPhase.DISCOVERING, 0, 0, 0, 0, List.of(), null);
    }

    @Override
    public Optional<CheckAccessStatus> checkAccessStatus() {
        return statuses.get();
    }
}
