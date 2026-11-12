package com.mcpscanner.checks.catalog.oauthrefreshgrant;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.auth.AuthStrategy;
import com.mcpscanner.checks.shared.auth.CredentialSubstitution;
import com.mcpscanner.checks.shared.auth.ToolsListProbe;
import com.mcpscanner.checks.shared.auth.oracle.BaselineEquivalenceOracle;
import com.mcpscanner.checks.shared.auth.oracle.OracleEvidence;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

final class TokenUsability {

    static final String MALFORMED_TOKEN = "mcp-scanner-invalid-token";

    private final Http http;
    private final HttpRequest probeSeed;
    private final Supplier<AuthStrategy> authStrategy;
    private final CredentialSubstitution credentials;
    private final BaselineEquivalenceOracle oracle = BaselineEquivalenceOracle.defaultOracle();
    private final ToolsListProbe toolsList = new ToolsListProbe(9_100);
    private HttpRequestResponse baseline;
    private List<HttpRequestResponse> refusalControls;

    TokenUsability(Http http, HttpRequest hostBaseRequest, Supplier<AuthStrategy> authStrategy) {
        this.http = http;
        this.probeSeed = hostBaseRequest;
        this.authStrategy = authStrategy;
        this.credentials = new CredentialSubstitution(authStrategy);
    }

    void primeWithLiveSession() {
        baseline();
        refusalControls();
    }

    JudgedToken judge(String accessToken) {
        HttpRequestResponse candidate = send(credentials.bearer(probeSeed, accessToken));
        Usability usability = Usability.of(oracle.judge(new OracleEvidence(baseline(), refusalControls(), candidate)));
        return new JudgedToken(usability, candidate);
    }

    private HttpRequestResponse baseline() {
        if (baseline == null) {
            Map<String, String> liveHeaders = authStrategy.get().headers();
            baseline = send(credentials.withIdentity(probeSeed, liveHeaders));
        }
        return baseline;
    }

    private List<HttpRequestResponse> refusalControls() {
        if (refusalControls == null) {
            refusalControls = List.of(send(anonymous()), send(credentials.bearer(probeSeed, MALFORMED_TOKEN)));
        }
        return refusalControls;
    }

    private HttpRequest anonymous() {
        return credentials.anonymous(probeSeed);
    }

    private HttpRequestResponse send(HttpRequest request) {
        return http.sendRequest(toolsList.on(request));
    }
}
