package com.mcpscanner.engine;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.scanner.AuditResult;
import burp.api.montoya.scanner.ConsolidationAction;
import burp.api.montoya.scanner.audit.insertionpoint.AuditInsertionPoint;
import burp.api.montoya.scanner.audit.issues.AuditIssue;
import burp.api.montoya.scanner.scancheck.ActiveScanCheck;
import com.mcpscanner.http.OutsideBurpSends;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static com.mcpscanner.engine.EngineFixtures.BOUND;

final class FakeCheck implements ActiveScanCheck {

    record Invocation(HttpRequestResponse base, AuditInsertionPoint insertionPoint, Http http) {
    }

    private final String name;
    private final List<Invocation> invocations = Collections.synchronizedList(new ArrayList<>());
    private List<AuditIssue> issues = List.of();
    private ConsolidationAction consolidation = ConsolidationAction.KEEP_BOTH;
    private RuntimeException failure;
    private boolean noResult;
    private CountDownLatch gate;
    private int probes;
    private int burpSends;
    private int outsideSends;

    private FakeCheck(String name) {
        this.name = name;
    }

    static FakeCheck named(String name) {
        return new FakeCheck(name);
    }

    FakeCheck reporting(AuditIssue... reported) {
        issues = List.of(reported);
        return this;
    }

    FakeCheck consolidating(ConsolidationAction action) {
        consolidation = action;
        return this;
    }

    FakeCheck throwing(RuntimeException thrown) {
        failure = thrown;
        return this;
    }

    FakeCheck returningNoResult() {
        noResult = true;
        return this;
    }

    FakeCheck sendingProbes(int count) {
        probes = count;
        return this;
    }

    FakeCheck sending(int throughBurp, int outsideBurp) {
        burpSends = throughBurp;
        outsideSends = outsideBurp;
        return this;
    }

    FakeCheck blockingUntil(CountDownLatch released) {
        gate = released;
        return this;
    }

    List<Invocation> invocations() {
        return List.copyOf(invocations);
    }

    @Override
    public String checkName() {
        return name;
    }

    @Override
    public AuditResult doCheck(HttpRequestResponse base, AuditInsertionPoint insertionPoint, Http http) {
        invocations.add(new Invocation(base, insertionPoint, http));
        for (int probe = 0; probe < probes; probe++) {
            http.sendRequest(base.request());
        }
        IntStream.range(0, burpSends).forEach(index -> http.sendRequest(base.request()));
        reportOutsideBurpSendsFromAnotherThread(http, base);
        awaitGate();
        if (failure != null) {
            throw failure;
        }
        return noResult ? null : AuditResult.auditResult(issues);
    }

    @Override
    public ConsolidationAction consolidateIssues(AuditIssue existingIssue, AuditIssue newIssue) {
        return consolidation;
    }

    private void reportOutsideBurpSendsFromAnotherThread(Http http, HttpRequestResponse base) {
        Thread worker = new Thread(() -> IntStream.range(0, outsideSends)
                .forEach(index -> OutsideBurpSends.report(http, base)));
        worker.start();
        try {
            worker.join();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private void awaitGate() {
        if (gate == null) {
            return;
        }
        try {
            gate.await(BOUND.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while gated", interrupted);
        }
    }
}
