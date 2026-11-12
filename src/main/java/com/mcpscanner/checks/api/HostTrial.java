package com.mcpscanner.checks.api;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.message.HttpRequestResponse;

import java.util.Optional;

public interface HostTrial {

    Optional<SkipReason> whyNotApplicable(HttpRequestResponse hostBaseRequestResponse);

    HostTrialResult run(HttpRequestResponse hostBaseRequestResponse, Http http);

    default boolean appliesTo(HttpRequestResponse hostBaseRequestResponse) {
        return whyNotApplicable(hostBaseRequestResponse).isEmpty();
    }

    default String subject(HttpRequestResponse hostBaseRequestResponse) {
        return SkipSubject.of(hostBaseRequestResponse);
    }

    default HostSettlement settlementOnFailure() {
        return HostSettlement.NOT_PROBED;
    }
}
