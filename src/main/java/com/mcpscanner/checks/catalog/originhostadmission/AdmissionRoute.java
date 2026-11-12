package com.mcpscanner.checks.catalog.originhostadmission;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.checks.shared.auth.CredentialHeaders;
import com.mcpscanner.checks.shared.session.StreamingFallback;

import java.util.function.UnaryOperator;

record AdmissionRoute(String label, UnaryOperator<HttpRequest> delivery, AdmissionTrial trial) {

    static AdmissionRoute direct(CredentialHeaders credentials, StreamingFallback streamedReplies) {
        return new AdmissionRoute("direct", ProbeDelivery.direct(credentials),
                new AdmissionTrial(AdmissionProbe.STANDARD_SET, streamedReplies));
    }

    static AdmissionRoute ownSseStream(CredentialHeaders credentials, StreamingFallback streamedReplies) {
        return new AdmissionRoute("own SSE stream, Origin probes only (the SSE client cannot send a Host header)",
                ProbeDelivery.ownStream(credentials), new AdmissionTrial(AdmissionProbe.ORIGIN_SET, streamedReplies));
    }

    HttpRequest deliver(HttpRequest seed) {
        return delivery.apply(seed);
    }

    TrialOutcome run(HttpRequest delivered, Http http) {
        return trial.run(delivered, http);
    }
}
