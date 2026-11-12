package com.mcpscanner.checks.catalog.oauthrefreshgrant;

import com.mcpscanner.auth.grant.ClientType;

import java.util.List;
import java.util.Optional;

final class RefreshFindings {

    private final AuthorizationServerMetadata metadata;

    RefreshFindings(AuthorizationServerMetadata metadata) {
        this.metadata = metadata;
    }

    Optional<ClientAuthenticationFinding> clientAuthentication(RefreshEvidence evidence) {
        if (!evidence.controlsHold() || !evidence.clientType().isConfidential()) {
            return Optional.empty();
        }
        List<JudgedRefresh> accepted = evidence.unauthenticatedAttempts().stream()
                .filter(JudgedRefresh::issued)
                .toList();
        return accepted.isEmpty() ? Optional.empty() : Optional.of(new ClientAuthenticationFinding(
                evidence.clientType(), confidentialityEstablished(evidence.clientType()), accepted, evidence));
    }

    Optional<ScopeExpansionFinding> scopeExpansion(RefreshEvidence evidence) {
        if (!evidence.controlsHold()) {
            return Optional.empty();
        }
        return evidence.scopeProbe()
                .filter(ScopeProbe::expanded)
                .filter(probe -> probe.refresh().usability() != Usability.NOT_USABLE)
                .map(probe -> new ScopeExpansionFinding(probe, evidence));
    }

    private boolean confidentialityEstablished(ClientType clientType) {
        return clientType == ClientType.CONFIDENTIAL_REGISTERED || metadata.admitsOnlyConfidentialClients();
    }
}
