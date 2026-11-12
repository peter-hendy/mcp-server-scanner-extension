package com.mcpscanner.engine;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.scanner.audit.insertionpoint.AuditInsertionPoint;
import burp.api.montoya.scanner.audit.insertionpoint.AuditInsertionPointProvider;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

final class InvocationPlanner {

    private final AuditInsertionPointProvider insertionPoints;
    private final RequestNames requestNames;

    InvocationPlanner(AuditInsertionPointProvider insertionPoints, RequestNames requestNames) {
        this.insertionPoints = insertionPoints;
        this.requestNames = requestNames;
    }

    List<CheckInvocation> plan(HttpRequestResponse base, List<RegisteredCheck> checks) {
        List<AuditInsertionPoint> points = checks.stream().anyMatch(RegisteredCheck::perInsertionPoint)
                ? insertionPoints.provideInsertionPoints(base)
                : List.of();
        String requestName = requestNames.nameOf(base.request());
        return checks.stream().flatMap(check -> invocations(check, base, requestName, points)).toList();
    }

    private static Stream<CheckInvocation> invocations(RegisteredCheck check, HttpRequestResponse base,
                                                       String requestName, List<AuditInsertionPoint> points) {
        if (check.perInsertionPoint()) {
            return points.stream().map(point ->
                    new CheckInvocation(check, base, requestName, Optional.of(point)));
        }
        return Stream.of(new CheckInvocation(check, base, requestName, Optional.empty()));
    }
}
