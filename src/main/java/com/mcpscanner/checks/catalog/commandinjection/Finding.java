package com.mcpscanner.checks.catalog.commandinjection;

import burp.api.montoya.collaborator.Interaction;
import burp.api.montoya.http.message.HttpRequestResponse;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

record Finding(FindingKind kind, FieldSubject subject, String technique, List<HttpRequestResponse> evidence,
               List<Interaction> interactions) {

    Finding {
        evidence = evidence.stream().filter(Objects::nonNull).toList();
        interactions = List.copyOf(interactions);
    }

    Finding(FindingKind kind, FieldSubject subject, String technique, List<HttpRequestResponse> evidence) {
        this(kind, subject, technique, evidence, List.of());
    }

    static Finding proving(FindingKind kind, FieldSubject subject, String technique, List<String> proofs,
                           InjectionReply... replies) {
        return new Finding(kind, subject, technique,
                Arrays.stream(replies).map(reply -> reply.exchangeHighlighting(proofs)).toList());
    }
}
