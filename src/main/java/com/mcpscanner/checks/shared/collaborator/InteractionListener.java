package com.mcpscanner.checks.shared.collaborator;

@FunctionalInterface
public interface InteractionListener {

    WatchDecision interacted(ObservedInteractions soFar);
}
