package com.mcpscanner.checks.catalog.unauthenticatedrequestexecution;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.HttpService;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.scanner.audit.issues.AuditIssue;
import com.mcpscanner.auth.AuthStrategy;
import com.mcpscanner.checks.api.CheckDescriptor;
import com.mcpscanner.checks.api.CheckLog;
import com.mcpscanner.checks.api.ConnectionScoped;
import com.mcpscanner.checks.api.ProbeStops;
import com.mcpscanner.checks.api.RequestCheck;
import com.mcpscanner.checks.api.SkipReason;
import com.mcpscanner.checks.api.SkipSubject;
import com.mcpscanner.checks.shared.auth.AuthVariantSuite;
import com.mcpscanner.checks.shared.auth.ClientAddressVariants;
import com.mcpscanner.checks.shared.auth.CredentialHeaders;
import com.mcpscanner.checks.shared.auth.CredentialStripper;
import com.mcpscanner.checks.shared.auth.CredentialSubstitution;
import com.mcpscanner.checks.shared.auth.VariantFamily;
import com.mcpscanner.checks.shared.auth.oracle.BaselineEquivalenceOracle;
import com.mcpscanner.checks.shared.probe.AuthenticatedBaseline;
import com.mcpscanner.checks.shared.probe.BaselineAcquisition;
import com.mcpscanner.checks.shared.probe.ControlRefusal;
import com.mcpscanner.checks.shared.probe.IndependentSessionSender;
import com.mcpscanner.checks.shared.probe.ProbeChannel;
import com.mcpscanner.checks.shared.session.StreamingFallback;
import com.mcpscanner.checks.shared.stream.EventStreams;
import com.mcpscanner.logging.McpEventLog;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Stream;

public final class UnauthenticatedRequestExecutionCheck implements RequestCheck, ConnectionScoped {

    static final int REQUEST_BUDGET = 240;
    static final int FORWARDED_CLIENT_BUDGET = 12;
    static final Duration SWEEP_MEMO_LIFETIME = Duration.ofMinutes(10);

    private static final SkipReason NOT_A_JSON_RPC_POST = SkipReason.notApplicable("needs a JSON-RPC 2.0 POST");

    private final CheckLog log;
    private final Supplier<AuthStrategy> authStrategy;
    private final Supplier<Optional<HttpService>> streamProxy;
    private final AuthVariantSuite suite;
    private final CredentialStripper stripper;
    private final BaselineEquivalenceOracle oracle = BaselineEquivalenceOracle.defaultOracle();
    private final UnauthenticatedRequestExecutionIssues issues = new UnauthenticatedRequestExecutionIssues(
            UnauthenticatedRequestExecutionDescriptor.DESCRIPTOR.references());
    private final AuthenticatedBaseline authenticatedBaseline;
    private final SweepMemo sweepMemo = newSweepMemo();
    private final EnforcementEvidence enforcement = new EnforcementEvidence(oracle);
    private final ClientAddressVariants clientAddresses = new ClientAddressVariants();
    private final ScopeClaims forwardedClientScopes = new ScopeClaims();
    private final ScopeClaims streamScopes = new ScopeClaims();
    private final StreamAccessTier streamTier;
    private final StreamingFallback streamedReplies = StreamingFallback.outsideBurp();

    public UnauthenticatedRequestExecutionCheck(McpEventLog eventLog, Supplier<AuthStrategy> authStrategy,
                                                Supplier<Optional<HttpService>> streamProxy,
                                                EventStreams streams) {
        this.log = new CheckLog(eventLog, UnauthenticatedRequestExecutionDescriptor.DESCRIPTOR.id());
        this.authStrategy = authStrategy;
        this.streamProxy = streamProxy;
        String canary = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        this.suite = new AuthVariantSuite(authStrategy, canary);
        this.stripper = new CredentialStripper(authStrategy);
        this.authenticatedBaseline = new AuthenticatedBaseline(authStrategy);
        this.streamTier = new StreamAccessTier(streams, new CredentialHeaders(authStrategy),
                new CredentialSubstitution(authStrategy), enforcement, streamScopes);
    }

    @Override
    public CheckDescriptor descriptor() {
        return UnauthenticatedRequestExecutionDescriptor.DESCRIPTOR;
    }

    static SweepMemo newSweepMemo() {
        return new SweepMemo(new SettledRejection(), Clock.systemUTC(), SWEEP_MEMO_LIFETIME);
    }

    @Override
    public void clearConnectionState() {
        sweepMemo.clear();
        enforcement.clear();
        forwardedClientScopes.clear();
        streamScopes.clear();
    }

    @Override
    public List<AuditIssue> audit(HttpRequestResponse baseRequestResponse, Http http) {
        Optional<SeedMessage> message = SeedMessage.of(baseRequestResponse.request());
        if (message.isEmpty()) {
            log.notRun(SkipSubject.of(baseRequestResponse), NOT_A_JSON_RPC_POST);
            return List.of();
        }
        return audit(baseRequestResponse, message.get(), streamedReplies.around(http)).stream()
                .map(issues::render)
                .toList();
    }

    private Set<VariantFamily> sweepFamilies() {
        return streamProxy.get().isPresent() ? SweepFamilies.forStreamProxy() : SweepFamilies.forDirectDelivery();
    }

    private List<Finding> audit(HttpRequestResponse base, SeedMessage message, Http http) {
        ProbeChannel channel = new ProbeChannel(http, REQUEST_BUDGET);
        IndependentSessionSender sender = new IndependentSessionSender(channel, oracle);
        HttpRequestResponse baseline = new BaselineAcquisition(authenticatedBaseline, channel, sender, oracle)
                .acquire(base);
        AuditSubject subject = new AuditSubject(baseline, message,
                CredentialPresence.configured(authStrategy.get(), base.request()));
        ProbeChannel forwardedChannel = new ProbeChannel(http, FORWARDED_CLIENT_BUDGET);
        Optional<Finding> forwardedClient = forwardedClientTier(forwardedChannel).judge(subject);
        Optional<Finding> execution = executionAudit(channel, sender).run(subject);
        Optional<Finding> stream = streamAccess(subject, channel, http);
        String probed = SkipSubject.of(base);
        new ProbeStops(log, forwardedChannel, "the forwarded-client tier on " + probed).reportedExhaustion();
        new ProbeStops(log, channel, probed).reportedExhaustion();
        return Stream.of(forwardedClient, execution, stream).flatMap(Optional::stream).toList();
    }

    private Optional<Finding> streamAccess(AuditSubject subject, ProbeChannel channel, Http http) {
        if (streamProxy.get().isPresent()) {
            return Optional.empty();
        }
        return streamTier.judge(subject, http, channel, new EnforcementProbe(channel, suite));
    }

    private ForwardedClientTier forwardedClientTier(ProbeChannel channel) {
        return new ForwardedClientTier(clientAddresses, authenticatedBaseline, channel, oracle, forwardedClientScopes);
    }

    private ExecutionAudit executionAudit(ProbeChannel channel, IndependentSessionSender sender) {
        ControlRefusal controlRefusal = new ControlRefusal(channel, oracle);
        AnonymousRecheck anonymousRecheck = new AnonymousRecheck(suite, sender, controlRefusal);
        SweepVariants sweepVariants = new SweepVariants(suite, new RetainedSessionVariant(stripper), sweepFamilies());
        BypassSweep sweep = new BypassSweep(sweepVariants, sweepMemo, sender, channel, oracle, anonymousRecheck);
        AnonymousExecutionTier anonymousTier = new AnonymousExecutionTier(oracle, enforcement,
                new EnforcementProbe(channel, suite));
        return new ExecutionAudit(suite, sender, controlRefusal, anonymousTier,
                new InvalidCredentialTier(suite, sender, anonymousRecheck, oracle), sweep, enforcement);
    }
}
