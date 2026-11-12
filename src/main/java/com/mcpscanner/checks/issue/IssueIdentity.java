package com.mcpscanner.checks.issue;

import burp.api.montoya.scanner.ConsolidationAction;
import burp.api.montoya.scanner.audit.issues.AuditIssue;

import java.util.Objects;
import java.util.function.UnaryOperator;

public final class IssueIdentity {

    private static final String PARAGRAPH_END = "</p>";

    private IssueIdentity() {
    }

    public static boolean sameSubject(AuditIssue existing, AuditIssue candidate) {
        return sameNameAnd(existing, candidate, IssueIdentity::firstParagraph);
    }

    public static boolean sameDetail(AuditIssue existing, AuditIssue candidate) {
        return sameNameAnd(existing, candidate, UnaryOperator.identity());
    }

    public static ConsolidationAction keepExistingWhen(boolean sameIssue) {
        return sameIssue ? ConsolidationAction.KEEP_EXISTING : ConsolidationAction.KEEP_BOTH;
    }

    public static ConsolidationAction keepStrongerOfSameSubject(AuditIssue existing, AuditIssue candidate) {
        if (!sameSubject(existing, candidate)) {
            return ConsolidationAction.KEEP_BOTH;
        }
        return IssueRating.of(candidate).isStrongerThan(IssueRating.of(existing))
                ? ConsolidationAction.KEEP_NEW
                : ConsolidationAction.KEEP_EXISTING;
    }

    private static boolean sameNameAnd(AuditIssue existing, AuditIssue candidate, UnaryOperator<String> identity) {
        String detail = existing.detail();
        String otherDetail = candidate.detail();
        return Objects.equals(existing.name(), candidate.name())
                && detail != null && otherDetail != null
                && identity.apply(detail).equals(identity.apply(otherDetail));
    }

    private static String firstParagraph(String detail) {
        int end = detail.indexOf(PARAGRAPH_END);
        return end < 0 ? detail : detail.substring(0, end);
    }
}
