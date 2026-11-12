package com.mcpscanner.checks.catalog.commandinjection;

import burp.api.montoya.http.message.HttpRequestResponse;
import com.mcpscanner.checks.shared.collaborator.ObservedInteractions;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

record PendingProbe(FieldSubject subject, Map<String, HttpRequestResponse> sentByPayloadId) {

    PendingProbe {
        sentByPayloadId = Map.copyOf(sentByPayloadId);
    }

    Finding finding(ObservedInteractions observed) {
        List<HttpRequestResponse> evidence = observed.inArrivalOrder().stream()
                .map(interaction -> sentByPayloadId.get(interaction.id().toString()))
                .distinct()
                .toList();
        return new Finding(FindingKind.SHELL_OUT_OF_BAND, subject, technique(observed), evidence,
                observed.inArrivalOrder());
    }

    private static String technique(ObservedInteractions observed) {
        return observed.inArrivalOrder().stream()
                .map(interaction -> String.valueOf(interaction.type()))
                .distinct()
                .sorted()
                .collect(Collectors.joining(" and ", "Collaborator ", " interaction"));
    }
}
