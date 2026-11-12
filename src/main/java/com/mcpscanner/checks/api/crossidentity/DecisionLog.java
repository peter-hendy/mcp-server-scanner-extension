package com.mcpscanner.checks.api.crossidentity;

import com.mcpscanner.checks.api.CheckLog;
import com.mcpscanner.checks.api.SkipReason;
import com.mcpscanner.checks.api.SkipSubject;

public final class DecisionLog {

    private final CheckLog log;
    private final String subject;
    private final String skipSubject;

    public DecisionLog(CheckLog log, String subject) {
        this(log, subject, subject);
    }

    private DecisionLog(CheckLog log, String subject, String skipSubject) {
        this.log = log;
        this.subject = subject;
        this.skipSubject = skipSubject;
    }

    public static DecisionLog of(CheckLog log, OperationSeed seed) {
        return new DecisionLog(log, seed.label(), SkipSubject.of(seed.request()));
    }

    public DecisionLog about(String principalName) {
        return new DecisionLog(log, subject + " as " + principalName, skipSubject + " as " + principalName);
    }

    public void record(String decision) {
        log.debug(subject + ": " + decision);
    }

    public void summarize(String summary) {
        log.info(subject + ": " + summary);
    }

    public void notRun(SkipReason reason) {
        log.notRun(skipSubject, reason);
    }
}
