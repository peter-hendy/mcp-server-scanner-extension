package com.mcpscanner.cvetargets.run;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.message.HttpRequestResponse;
import com.mcpscanner.automation.ProbeCounter;
import com.mcpscanner.checks.api.CheckRunner;
import com.mcpscanner.checks.api.InsertionPointSource;
import com.mcpscanner.cvetargets.requests.ArmEndpoint;
import com.mcpscanner.cvetargets.requests.BaseRequestScope;
import com.mcpscanner.cvetargets.requests.BaseRequestSet;
import com.mcpscanner.cvetargets.requests.BaseRequestSource;

import java.time.Duration;
import java.util.List;
import java.util.function.Function;

public final class CheckRunnerAudit implements ArmAudit {

    private final BaseRequestSource requestSource;
    private final Function<Duration, Http> httpFactory;
    private final InsertionPointSource insertionPoints;
    private final ArmProbeService probeService = new ArmProbeService();
    private final ArmLateIssues lateIssues = new ArmLateIssues();

    public CheckRunnerAudit(BaseRequestSource requestSource, Function<Duration, Http> httpFactory,
                            InsertionPointSource insertionPoints) {
        this.requestSource = requestSource;
        this.httpFactory = httpFactory;
        this.insertionPoints = insertionPoints;
    }

    public ArmProbeService probeService() {
        return probeService;
    }

    public ArmLateIssues lateIssues() {
        return lateIssues;
    }

    @Override
    public String mode() {
        return "direct";
    }

    @Override
    public ArmObservation audit(ArmRun run, ArmEndpoint endpoint, ArmTeardown teardown, ArmProgress progress) {
        Http http = httpFactory.apply(run.target().manifest().readTimeout());
        BaseRequestScope scope = new BaseRequestScope(run.identities().routed(run.target().manifest().baseSeeds()),
                run.target().manifest().catalogLimit(), progress::note);
        try (BaseRequestSet baseRequests = requestSource.open(endpoint, scope, http)) {
            requireAnsweredBaseRequest(baseRequests.requests());
            probeService.bind(teardown, baseRequests::probeService, baseRequests::sseProbeStreams);
            ProbeCounter probes = new ProbeCounter(baseRequests::reachedTarget);
            List<IssueSummary> issues = new CheckRunner(
                    probes.wrap(new TornDownArmHttp(baseRequests.route(http), teardown)), insertionPoints,
                    lateIssues)
                    .run(run.check(), baseRequests.requests()).stream()
                    .map(IssueSummary::of)
                    .toList();
            return new ArmObservation(issues, baseRequests.requests().size(), baseRequests.unansweredCount(),
                    new ProbeTally(probes.sent(), probes.reached()), null);
        } finally {
            probeService.clear(teardown);
        }
    }

    private static void requireAnsweredBaseRequest(List<HttpRequestResponse> baseRequests) {
        boolean answered = baseRequests.stream()
                .anyMatch(base -> base.response() != null && base.response().statusCode() / 100 == 2);
        if (!answered) {
            throw new IllegalStateException("no base request was answered with 2xx (" + baseRequests.size()
                    + " sent), so the check exercised nothing");
        }
    }
}
