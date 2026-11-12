package com.mcpscanner.checks.catalog.sessionlifecycle;

import burp.api.montoya.http.message.HttpRequestResponse;
import com.mcpscanner.checks.api.CheckLog;
import com.mcpscanner.checks.api.SkipReason;
import com.mcpscanner.checks.api.SkipSubject;

record SkipLog(CheckLog log, String subject) {

    static SkipLog ofBase(CheckLog log, HttpRequestResponse base) {
        return new SkipLog(log, SkipSubject.of(base));
    }

    static SkipLog ofTest(CheckLog log, String test, String url) {
        return new SkipLog(log, "the " + test + " test on " + url);
    }

    void notRun(SkipReason reason) {
        log.notRun(subject, reason);
    }

    void notRun(SkipReason reason, ProbeTraffic traffic) {
        notRun(traffic.throttled() ? SkipReason.rateLimited() : reason);
    }

    void failed(RuntimeException failure) {
        log.warn(subject + " failed: " + failure);
    }
}
