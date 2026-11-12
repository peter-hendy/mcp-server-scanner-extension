package com.mcpscanner.checks.api;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.scanner.audit.issues.AuditIssue;

import java.util.List;

public interface RetryingHostCheck extends HostCheck {

    @Override
    List<HostTrial> trials();

    @Override
    default boolean appliesTo(HttpRequestResponse hostBaseRequestResponse) {
        return trials().stream().anyMatch(trial -> trial.appliesTo(hostBaseRequestResponse));
    }

    @Override
    default String appliesOnlyTo() {
        return "requests one of its trials applies to";
    }

    @Override
    default List<AuditIssue> audit(HttpRequestResponse hostBaseRequestResponse, Http http) {
        return trials().stream()
                .filter(trial -> trial.appliesTo(hostBaseRequestResponse))
                .flatMap(trial -> trial.run(hostBaseRequestResponse, http).issues().stream())
                .toList();
    }
}
