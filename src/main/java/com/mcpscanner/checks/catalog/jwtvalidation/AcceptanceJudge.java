package com.mcpscanner.checks.catalog.jwtvalidation;

import burp.api.montoya.http.message.HttpRequestResponse;
import com.mcpscanner.checks.shared.auth.oracle.BaselineEquivalenceOracle;
import com.mcpscanner.checks.shared.auth.oracle.OracleEvidence;
import com.mcpscanner.checks.shared.auth.oracle.OracleVerdict;

import java.util.ArrayList;
import java.util.List;

final class AcceptanceJudge {

    private final BaselineEquivalenceOracle oracle;
    private final HttpRequestResponse baseline;
    private final List<HttpRequestResponse> refusalControls;

    AcceptanceJudge(BaselineEquivalenceOracle oracle, HttpRequestResponse baseline,
                    List<HttpRequestResponse> refusalControls) {
        this.oracle = oracle;
        this.baseline = baseline;
        this.refusalControls = List.copyOf(refusalControls);
    }

    OracleVerdict baselineVerdict() {
        return oracle.judge(new OracleEvidence(baseline, refusalControls, baseline));
    }

    boolean accepts(HttpRequestResponse candidate) {
        return accepts(candidate, List.of());
    }

    boolean accepts(HttpRequestResponse candidate, List<HttpRequestResponse> extraControls) {
        List<HttpRequestResponse> controls = new ArrayList<>(refusalControls);
        controls.addAll(extraControls);
        return oracle.judge(new OracleEvidence(baseline, controls, candidate)).isHit();
    }
}
