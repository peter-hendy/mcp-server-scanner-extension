package com.mcpscanner.checks.shared.collaborator;

import burp.api.montoya.collaborator.CollaboratorPayload;
import burp.api.montoya.collaborator.Interaction;

import java.util.List;

public record ObservedInteractions(List<Interaction> inArrivalOrder) {

    public ObservedInteractions {
        inArrivalOrder = List.copyOf(inArrivalOrder);
    }

    public static ObservedInteractions none() {
        return new ObservedInteractions(List.of());
    }

    public List<Interaction> of(CollaboratorPayload payload) {
        String payloadId = payload.id().toString();
        return inArrivalOrder.stream().filter(interaction -> interaction.id().toString().equals(payloadId)).toList();
    }

    public boolean isEmpty() {
        return inArrivalOrder.isEmpty();
    }
}
