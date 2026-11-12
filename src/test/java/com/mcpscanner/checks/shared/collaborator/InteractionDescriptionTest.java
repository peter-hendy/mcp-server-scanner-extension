package com.mcpscanner.checks.shared.collaborator;

import burp.api.montoya.collaborator.InteractionType;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InteractionDescriptionTest {

    @BeforeEach
    void installMontoya() {
        MontoyaTestFactory.install();
    }

    @Test
    void describesAnHttpInteractionByMethodAndPath() {
        HttpRequestResponse fetch = HttpRequestResponse.httpRequestResponse(
                HttpRequest.httpRequestFromUrl("http://a." + FakeCollaborator.DOMAIN + "/probe?x=1"), null);

        String described = InteractionDescription.of(FakeCollaborator.interaction("a", InteractionType.HTTP, fetch));

        assertThat(described).isEqualTo("HTTP request from 127.0.0.1 at 2026-11-06T12:00Z: GET /probe?x=1");
    }

    @Test
    void describesAnInteractionWithoutAnHttpRequestByItsType() {
        String described = InteractionDescription.of(FakeCollaborator.interaction("a", InteractionType.DNS, null));

        assertThat(described).isEqualTo("DNS interaction from 127.0.0.1 at 2026-11-06T12:00Z");
    }
}
