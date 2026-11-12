package com.mcpscanner.checks.catalog.originhostadmission;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.checks.shared.auth.CredentialHeaders;
import com.mcpscanner.checks.shared.net.HostReachability;

record TargetExposure(boolean locallyReachable, boolean credentialed, boolean tls) {

    static TargetExposure of(HttpRequest deliveredRequest, CredentialHeaders credentials) {
        return new TargetExposure(HostReachability.isLocallyReachable(deliveredRequest.httpService().host()),
                credentials.carriedBy(deliveredRequest), deliveredRequest.httpService().secure());
    }

    boolean reboundByABrowser() {
        return locallyReachable && !tls;
    }
}
