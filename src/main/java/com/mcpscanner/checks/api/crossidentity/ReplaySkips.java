package com.mcpscanner.checks.api.crossidentity;

import com.mcpscanner.checks.api.SkipReason;

public final class ReplaySkips {

    public static final SkipReason NOT_AN_OPERATION = SkipReason.notApplicable(
            "only tools/call and resources/read requests with a JSON-RPC id are replayed");
    public static final SkipReason NO_COMPARED_IDENTITY =
            SkipReason.prerequisiteMissing("no compared identities with credentials");
    public static final SkipReason WRITE_REPLAY_NOT_CONFIRMED =
            SkipReason.prerequisiteMissing("replaying this write operation was not confirmed");
    public static final SkipReason ANONYMOUS_UNANSWERED =
            SkipReason.transportFailure("the anonymous request got no response");
    public static final SkipReason ANONYMOUS_GETS_OWNER_RESULT =
            SkipReason.notApplicable("the anonymous request already gets the owner's result");

    private ReplaySkips() {
    }
}
