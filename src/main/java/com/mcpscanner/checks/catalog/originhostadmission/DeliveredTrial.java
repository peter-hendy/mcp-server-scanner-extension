package com.mcpscanner.checks.catalog.originhostadmission;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.checks.shared.session.SessionHeaders;

record DeliveredTrial(String label, HttpRequest delivered, TrialOutcome outcome) {

    boolean refusedInCapturedSession() {
        return outcome.baselineAnsweredUnsuccessfully() && SessionHeaders.carriedBy(delivered).isPresent();
    }
}
