package com.mcpscanner.client.servercard;

import com.fasterxml.jackson.databind.JsonNode;

import java.net.URI;

public sealed interface DocumentOutcome {

    URI url();

    record Found(URI url, JsonNode document) implements DocumentOutcome {
    }

    record NotFound(URI url, int statusCode) implements DocumentOutcome {
    }

    record Failed(URI url, String reason) implements DocumentOutcome {
    }
}
