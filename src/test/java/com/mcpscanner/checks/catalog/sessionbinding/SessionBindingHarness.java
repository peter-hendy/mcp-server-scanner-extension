package com.mcpscanner.checks.catalog.sessionbinding;

import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.scanner.audit.issues.AuditIssue;
import com.mcpscanner.auth.BearerTokenAuthStrategy;
import com.mcpscanner.checks.api.CheckLog;
import com.mcpscanner.checks.api.Principal;
import com.mcpscanner.checks.api.PrincipalRoster;
import com.mcpscanner.checks.api.ReplayPermission;
import com.mcpscanner.checks.api.crossidentity.FakeMcpTarget;
import com.mcpscanner.checks.api.crossidentity.FakeReply;
import com.mcpscanner.checks.api.crossidentity.TestPrincipals;
import com.mcpscanner.logging.McpEventLog;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class SessionBindingHarness {

    private static final Pattern DECISION = Pattern.compile(
            "^" + Pattern.quote(CheckLog.prefix(SessionBindingDescriptor.ID)) + "(.+?)(?: as (\\S+))?: ([^ ]+)$");

    private final McpEventLog eventLog = new McpEventLog(null);
    private final List<Principal> compared = new ArrayList<>();
    private final List<HttpRequest> bases = new ArrayList<>();
    private final List<AuditIssue> issues = new ArrayList<>();
    private Principal owner = TestPrincipals.owner(Optional.empty());
    private ReplayPermission permission = item -> true;
    private FakeMcpTarget target;
    private SessionBindingCheck check;

    static SessionBindingHarness withPeerB() {
        return new SessionBindingHarness().with(TestPrincipals.peer("B"));
    }

    SessionBindingHarness with(Principal principal) {
        compared.add(principal);
        return this;
    }

    SessionBindingHarness ownerMarker(String marker) {
        owner = TestPrincipals.owner(Optional.of(marker));
        return this;
    }

    SessionBindingHarness permitting(ReplayPermission value) {
        permission = value;
        return this;
    }

    SessionBindingHarness against(Function<HttpRequest, FakeReply> server) {
        target = new FakeMcpTarget(server);
        check = new SessionBindingCheck(eventLog, () -> new BearerTokenAuthStrategy("A"),
                () -> PrincipalRoster.of(owner, compared), permission);
        return this;
    }

    SessionBindingHarness run(Function<HttpRequest, FakeReply> server, HttpRequest seed) {
        return against(server).audit(seed);
    }

    SessionBindingHarness audit(HttpRequest seed) {
        bases.add(seed);
        issues.addAll(check.audit(target.exchange(seed), target.http()));
        return this;
    }

    void disconnect() {
        check.clearConnectionState();
    }

    List<AuditIssue> issues() {
        return List.copyOf(issues);
    }

    List<HttpRequest> sent() {
        return target.sent().stream().filter(request -> bases.stream().noneMatch(base -> base == request)).toList();
    }

    List<String> decisions() {
        return attributed().stream().map(Decision::name).toList();
    }

    List<String> decisionsOf(String principalName) {
        return attributed().stream().filter(decision -> principalName.equals(decision.principalName()))
                .map(Decision::name).toList();
    }

    List<String> infoLines() {
        return eventLog.snapshot().stream().filter(entry -> entry.level() == McpEventLog.Level.INFO)
                .map(McpEventLog.LogEntry::message).toList();
    }

    boolean firedFamily(String family) {
        return issues.stream().anyMatch(issue -> issue.detail().contains("Probe: " + family + "."));
    }

    private List<Decision> attributed() {
        return eventLog.snapshot().stream()
                .filter(entry -> entry.level() == McpEventLog.Level.DEBUG)
                .map(entry -> DECISION.matcher(entry.message()))
                .filter(Matcher::matches)
                .map(match -> new Decision(match.group(2), match.group(3)))
                .toList();
    }

    private record Decision(String principalName, String name) {
    }
}
