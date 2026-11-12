package com.mcpscanner.checks.api.crossidentity;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.checks.api.SkipReason;

import java.util.Optional;

public final class OwnerBaseline {

    private final ProbeKit kit;

    public OwnerBaseline(ProbeKit kit) {
        this.kit = kit;
    }

    public Optional<OperationReply> acquire() {
        Optional<HttpRequest> ownerRequest = kit.ownerRequest();
        return ownerRequest.isPresent() ? acquire(ownerRequest.get())
                : rejected("baseline_unusable", "the owner's probe session could not be opened");
    }

    private Optional<OperationReply> acquire(HttpRequest ownerRequest) {
        OperationReply first = kit.reply(ownerRequest);
        OperationReply second = kit.reply(ownerRequest);
        if (!first.success() || !second.success()) {
            return rejected("baseline_unusable", "the owner's request did not succeed");
        }
        if (!first.sameResult(second)) {
            return rejected("baseline_unstable", "the owner's request returned a different result each time");
        }
        if (!second.substantive()) {
            return rejected("baseline_empty", "the owner's request returned an empty result");
        }
        return Optional.of(second);
    }

    private Optional<OperationReply> rejected(String decision, String why) {
        kit.log().record(decision);
        kit.skippedUnlessStopped(SkipReason.prerequisiteMissing(why));
        return Optional.empty();
    }
}
