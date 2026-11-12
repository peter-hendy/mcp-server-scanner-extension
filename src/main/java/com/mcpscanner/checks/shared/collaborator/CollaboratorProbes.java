package com.mcpscanner.checks.shared.collaborator;

@FunctionalInterface
public interface CollaboratorProbes<T> {

    T send(PayloadSource payloads);
}
