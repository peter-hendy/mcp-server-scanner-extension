package com.mcpscanner.checks.catalog.outboundrequest;

import burp.api.montoya.collaborator.CollaboratorPayload;
import burp.api.montoya.http.message.HttpRequestResponse;

record SentProbe(ProbeForm form, CollaboratorPayload payload, HttpRequestResponse exchange) {

    String value() {
        return form.render(payload.toString());
    }
}
