package com.mcpscanner.checks.shared.collaborator;

import burp.api.montoya.collaborator.CollaboratorPayload;

import java.util.Optional;

@FunctionalInterface
public interface PayloadSource {

    Optional<CollaboratorPayload> newPayload();
}
