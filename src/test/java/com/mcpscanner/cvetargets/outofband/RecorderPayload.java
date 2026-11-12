package com.mcpscanner.cvetargets.outofband;

import burp.api.montoya.collaborator.CollaboratorPayload;
import burp.api.montoya.collaborator.CollaboratorServer;
import burp.api.montoya.collaborator.InteractionId;

import java.util.Optional;

record RecorderPayload(String identifier, String domain) implements CollaboratorPayload {

    @Override
    public InteractionId id() {
        return new RecorderInteractionId(identifier);
    }

    @Override
    public Optional<String> customData() {
        return Optional.empty();
    }

    @Override
    public Optional<CollaboratorServer> server() {
        return Optional.empty();
    }

    @Override
    public String toString() {
        return identifier + "." + domain;
    }
}
