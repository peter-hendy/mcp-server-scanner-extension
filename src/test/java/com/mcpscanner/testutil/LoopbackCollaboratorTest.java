package com.mcpscanner.testutil;

import burp.api.montoya.collaborator.CollaboratorPayload;
import burp.api.montoya.collaborator.InteractionType;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

class LoopbackCollaboratorTest {

    @Test
    void recordsAnHttpInteractionForTheHostOfAGeneratedPayload() throws Exception {
        try (LoopbackCollaborator collaborator = LoopbackCollaborator.start()) {
            CollaboratorPayload payload = collaborator.generatePayload();
            String port = payload.toString().substring(payload.toString().lastIndexOf(':') + 1);

            HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/"))
                    .header("Host", payload.toString()).GET().build(), HttpResponse.BodyHandlers.discarding());

            assertThat(payload.toString()).matches("[0-9a-f]{20}\\.localhost:\\d+");
            assertThat(collaborator.getAllInteractions()).singleElement().satisfies(interaction -> {
                assertThat(interaction.id().toString()).isEqualTo(payload.id().toString());
                assertThat(interaction.type()).isEqualTo(InteractionType.HTTP);
            });
        }
    }

    @Test
    void ignoresRequestsForOtherHosts() throws Exception {
        try (LoopbackCollaborator collaborator = LoopbackCollaborator.start()) {
            String port = collaborator.generatePayload().toString().replaceAll(".*:", "");

            HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/")).GET()
                    .build(), HttpResponse.BodyHandlers.discarding());

            assertThat(collaborator.getAllInteractions()).isEmpty();
        }
    }
}
