package com.mcpscanner.checks.catalog.commandinjection;

import burp.api.montoya.collaborator.CollaboratorClient;
import burp.api.montoya.collaborator.CollaboratorPayload;
import burp.api.montoya.collaborator.Interaction;
import burp.api.montoya.collaborator.InteractionId;
import burp.api.montoya.collaborator.InteractionType;

import java.net.InetAddress;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

final class FakeCollaborator {

    static final String DOMAIN = ".oastify.test";

    private final CollaboratorClient client = mock(CollaboratorClient.class);
    private final List<Interaction> interactions = new ArrayList<>();
    private final List<String> generatedHosts = new ArrayList<>();
    private int generated;

    FakeCollaborator() {
        when(client.generatePayload()).thenAnswer(invocation -> nextPayload());
        when(client.getAllInteractions()).thenAnswer(invocation -> List.copyOf(interactions));
    }

    CollaboratorClient client() {
        return client;
    }

    Optional<CollaboratorClient> available() {
        return Optional.of(client);
    }

    List<String> generatedHosts() {
        return List.copyOf(generatedHosts);
    }

    void resolve(String host) {
        if (host.endsWith(DOMAIN)) {
            interactions.add(interaction(host.substring(0, host.length() - DOMAIN.length()), InteractionType.DNS));
        }
    }

    void recordUnrelated(String id) {
        interactions.add(interaction(id, InteractionType.HTTP));
    }

    private CollaboratorPayload nextPayload() {
        String id = "payload" + generated++;
        CollaboratorPayload payload = mock(CollaboratorPayload.class);
        InteractionId interactionId = interactionId(id);
        when(payload.id()).thenReturn(interactionId);
        when(payload.toString()).thenReturn(id + DOMAIN);
        generatedHosts.add(id + DOMAIN);
        return payload;
    }

    private static Interaction interaction(String id, InteractionType type) {
        Interaction interaction = mock(Interaction.class);
        InteractionId interactionId = interactionId(id);
        when(interaction.id()).thenReturn(interactionId);
        when(interaction.type()).thenReturn(type);
        when(interaction.clientIp()).thenReturn(InetAddress.getLoopbackAddress());
        when(interaction.timeStamp()).thenReturn(ZonedDateTime.now());
        return interaction;
    }

    private static InteractionId interactionId(String id) {
        InteractionId interactionId = mock(InteractionId.class);
        when(interactionId.toString()).thenReturn(id);
        return interactionId;
    }
}
