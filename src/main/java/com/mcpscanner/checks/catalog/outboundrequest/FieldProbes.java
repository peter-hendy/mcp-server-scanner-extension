package com.mcpscanner.checks.catalog.outboundrequest;

import burp.api.montoya.collaborator.CollaboratorPayload;
import burp.api.montoya.core.ByteArray;
import burp.api.montoya.http.Http;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.scanner.audit.insertionpoint.AuditInsertionPoint;
import com.mcpscanner.checks.shared.collaborator.CollaboratorWatch;
import com.mcpscanner.checks.shared.collaborator.PayloadSource;
import com.mcpscanner.checks.shared.evidence.PayloadHighlights;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class FieldProbes {

    private final CollaboratorWatch watch;

    FieldProbes(CollaboratorWatch watch) {
        this.watch = watch;
    }

    List<SentProbe> send(AuditInsertionPoint point, Http http) {
        return watch.probing(payloads -> sendEveryForm(point, http, payloads));
    }

    private static List<SentProbe> sendEveryForm(AuditInsertionPoint point, Http http, PayloadSource payloads) {
        List<SentProbe> sent = new ArrayList<>();
        for (ProbeForm form : ProbeForm.values()) {
            Optional<CollaboratorPayload> payload = payloads.newPayload();
            payload.ifPresent(host -> sent.add(send(point, http, form, host)));
        }
        return sent;
    }

    private static SentProbe send(AuditInsertionPoint point, Http http, ProbeForm form, CollaboratorPayload host) {
        String payload = form.render(host.toString());
        HttpRequest probe = point.buildHttpRequestWithPayload(ByteArray.byteArray(payload));
        return new SentProbe(form, host, PayloadHighlights.ofPayload(exchange(http, probe), point, payload));
    }

    private static HttpRequestResponse exchange(Http http, HttpRequest probe) {
        try {
            return http.sendRequest(probe);
        } catch (RuntimeException failure) {
            return HttpRequestResponse.httpRequestResponse(probe, null);
        }
    }
}
