package com.mcpscanner.checks.catalog.originhostadmission;

import burp.api.montoya.http.message.HttpRequestResponse;

record ProbeObservation(AdmissionProbe probe, HttpRequestResponse exchange, ProbeVerdict verdict) {

    boolean admittedIn(AdmissionCategory category) {
        return verdict == ProbeVerdict.ADMITTED && probe.category() == category;
    }

    String summary() {
        return probe.label() + " -> " + verdict;
    }
}
