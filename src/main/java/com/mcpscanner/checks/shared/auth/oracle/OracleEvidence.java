package com.mcpscanner.checks.shared.auth.oracle;

import burp.api.montoya.http.message.HttpRequestResponse;

import java.util.List;
import java.util.Objects;

public record OracleEvidence(HttpRequestResponse authenticatedBaseline,
                             List<HttpRequestResponse> refusalControls,
                             HttpRequestResponse candidate) {

    public OracleEvidence {
        refusalControls = List.copyOf(Objects.requireNonNull(refusalControls, "refusalControls"));
    }
}
