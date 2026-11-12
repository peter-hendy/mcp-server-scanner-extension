package com.mcpscanner.checks.catalog.filetraversal;

import burp.api.montoya.http.message.HttpRequestResponse;

record TraversalAttempt(TraversalPayload payload, String sentValue, HttpRequestResponse exchange, TraversalReply reply) {

    Disclosure disclosure() {
        return payload.file().disclosureIn(reply.texts());
    }

    boolean rejectedWithoutDisclosure() {
        return reply.rejected() && disclosure() == Disclosure.NONE;
    }
}
