package com.mcpscanner.cvetargets.burp;

import com.mcpscanner.auth.AuthHeaders;
import com.mcpscanner.automation.CommandAuth;
import com.mcpscanner.automation.ScanAllCommand;
import com.mcpscanner.automation.SeedCall;
import com.mcpscanner.cvetargets.manifest.SeedRequest;
import com.mcpscanner.cvetargets.requests.ArmEndpoint;
import com.mcpscanner.cvetargets.run.ArmBoundAuth;
import com.mcpscanner.cvetargets.run.ArmRun;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class ScanAllCommands {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String OAUTH_NOT_HANDED_OVER =
            "Burp mode cannot hand a harness-obtained OAuth session to the extension in Burp yet";
    private static final String IDENTITY_SEED_NOT_ROUTED =
            "Burp mode cannot send a seed as another identity yet";

    private final boolean useBurpNetworking;
    private final BurpAuditConfiguration configuration;
    private final boolean allChecks;

    ScanAllCommands(boolean useBurpNetworking, BurpAuditConfiguration configuration) {
        this(useBurpNetworking, configuration, false);
    }

    ScanAllCommands(boolean useBurpNetworking, BurpAuditConfiguration configuration, boolean allChecks) {
        this.useBurpNetworking = useBurpNetworking;
        this.configuration = configuration;
        this.allChecks = allChecks;
    }

    Optional<String> notRunnable(ArmRun run) {
        boolean routedSeed = run.target().manifest().baseSeeds().stream().anyMatch(SeedRequest::sentAsIdentity);
        return credentialNotHandedOver(run).or(() -> routedSeed ? Optional.of(IDENTITY_SEED_NOT_ROUTED)
                : Optional.empty());
    }

    static Optional<String> credentialNotHandedOver(ArmRun run) {
        return run.auth() instanceof ArmBoundAuth ? Optional.of(OAUTH_NOT_HANDED_OVER) : Optional.empty();
    }

    ScanAllCommand forArm(ArmRun run, ArmEndpoint endpoint) {
        notRunnable(run).ifPresent(reason -> {
            throw new UnsupportedOperationException(reason);
        });
        return new ScanAllCommand(endpoint.uri().toString(), endpoint.transport(), auth(run.auth().headers()),
                Set.of(allChecks ? ScanAllCommand.EVERY_CHECK : run.checkId()),
                run.target().manifest().baseSeeds().stream()
                        .map(seed -> new SeedCall(seed.method(), seed.params()))
                        .toList(),
                useBurpNetworking, configuration.divertsBuiltInChecks(), ArmIdentityCommands.of(run),
                run.target().manifest().maxCatalogItems());
    }

    static CommandAuth auth(Map<String, String> headers) {
        String authorization = headers.get(AuthHeaders.AUTHORIZATION_HEADER);
        if (headers.size() == 1 && authorization != null && authorization.startsWith(BEARER_PREFIX)) {
            return new CommandAuth(authorization.substring(BEARER_PREFIX.length()), Map.of());
        }
        return new CommandAuth(null, headers);
    }
}
