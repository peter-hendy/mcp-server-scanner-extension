package com.mcpscanner.cvetargets.mining;

import com.mcpscanner.access.mining.MiningCall;
import com.mcpscanner.access.mining.MiningReply;
import com.mcpscanner.access.mining.MiningSender;
import com.mcpscanner.checks.api.Principal;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class RecordingMiningSender implements MiningSender {

    private static final int SNIPPET = 400;

    private final MiningSender delegate;
    private final List<SentCall> calls = new CopyOnWriteArrayList<>();

    public RecordingMiningSender(MiningSender delegate) {
        this.delegate = delegate;
    }

    @Override
    public MiningReply send(Principal identity, MiningCall call) {
        MiningReply reply = delegate.send(identity, call);
        calls.add(new SentCall(identity.id(), call.operation(), call.params().path("arguments").toString(),
                reply.status(), reply.usable(), snippet(reply.result().toString())));
        return reply;
    }

    public List<SentCall> calls() {
        return List.copyOf(calls);
    }

    private static String snippet(String text) {
        return text.length() <= SNIPPET ? text : text.substring(0, SNIPPET) + "…";
    }

    public record SentCall(String identityId, String operation, String arguments, int status, boolean usable,
                           String result) {
    }
}
