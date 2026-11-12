package com.mcpscanner.scan.request;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mcpscanner.scan.UriTemplateExpansion;

import java.net.URI;
import java.util.Map;
import java.util.Optional;

public record ClientRegistrationMessage(URI registrationEndpoint, ObjectNode metadata) implements ScanMessage {

    public ClientRegistrationMessage {
        metadata = metadata.deepCopy();
    }

    @Override
    public ObjectNode metadata() {
        return metadata.deepCopy();
    }

    @Override
    public RequestFamily family() {
        return RequestFamily.CLIENT_REGISTRATION;
    }

    @Override
    public String subject() {
        return "OAuth client registration " + registrationEndpoint;
    }

    @Override
    public Optional<UriTemplateExpansion.Result> templateExpansion() {
        return Optional.empty();
    }

    @Override
    public HttpRequest renderWith(ScanMessageRenderer renderer, String mcpEndpoint,
                                  Map<String, String> sessionHeaders) {
        return renderer.renderClientRegistration(this);
    }
}
