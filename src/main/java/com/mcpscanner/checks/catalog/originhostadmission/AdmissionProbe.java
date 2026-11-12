package com.mcpscanner.checks.catalog.originhostadmission;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.mcp.HeaderMutation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

record AdmissionProbe(String label, AdmissionCategory category, Map<String, String> headers) {

    private static final String ORIGIN = "Origin";
    private static final String HOST = "Host";
    private static final String ATTACKER_HOST = "attacker.example";

    static final List<AdmissionProbe> ORIGIN_SET = List.of(
            origin("http://evil.example"),
            origin("null"),
            origin("http://attacker.example:1337"));

    static final List<AdmissionProbe> STANDARD_SET = Stream.concat(ORIGIN_SET.stream(), Stream.of(
            new AdmissionProbe("Host: " + ATTACKER_HOST, AdmissionCategory.HOST, Map.of(HOST, ATTACKER_HOST)),
            hostAndOrigin())).toList();

    static final List<AdmissionProbe> STREAM_ORIGIN_SET = ORIGIN_SET.stream()
            .map(probe -> new AdmissionProbe(probe.label(), AdmissionCategory.STREAM_ORIGIN, probe.headers()))
            .toList();

    AdmissionProbe {
        headers = Map.copyOf(headers);
    }

    HttpRequest applyTo(HttpRequest request) {
        return HeaderMutation.apply(request, List.of(), headers);
    }

    private static AdmissionProbe origin(String value) {
        return new AdmissionProbe(ORIGIN + ": " + value, AdmissionCategory.ORIGIN, Map.of(ORIGIN, value));
    }

    private static AdmissionProbe hostAndOrigin() {
        Map<String, String> pair = new LinkedHashMap<>();
        pair.put(HOST, ATTACKER_HOST);
        pair.put(ORIGIN, "http://" + ATTACKER_HOST);
        return new AdmissionProbe(HOST + ": " + ATTACKER_HOST + " + " + ORIGIN + ": http://" + ATTACKER_HOST,
                AdmissionCategory.HOST_AND_ORIGIN, pair);
    }
}
