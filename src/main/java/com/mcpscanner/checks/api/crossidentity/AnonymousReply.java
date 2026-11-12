package com.mcpscanner.checks.api.crossidentity;

import burp.api.montoya.http.message.requests.HttpRequest;

public record AnonymousReply(HttpRequest request, OperationReply reply) {

    public static AnonymousReply fetch(ProbeKit kit) {
        HttpRequest request = kit.sessions().fresh(kit.anonymousSessionless()).orElseGet(kit::anonymousSessionless);
        return new AnonymousReply(request, kit.reply(request));
    }

    public boolean refused() {
        return !reply.success();
    }
}
