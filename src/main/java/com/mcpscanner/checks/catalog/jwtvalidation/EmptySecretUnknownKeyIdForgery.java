package com.mcpscanner.checks.catalog.jwtvalidation;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mcpscanner.checks.shared.random.RandomHex;

import java.util.List;

final class EmptySecretUnknownKeyIdForgery implements ForgeryStrategy {

    private static final String KEY_ID_PREFIX = "mcp-scanner-unknown-";
    private static final int KEY_ID_BYTES = 8;

    @Override
    public List<ForgeryAttempt> attemptsFor(ForgeryContext context) {
        ObjectNode header = ForgedHeader.withKeyId("HS256", KEY_ID_PREFIX + RandomHex.of(KEY_ID_BYTES));
        return List.of(ForgeryAttempt.controlledBy(
                new HmacSignature("an HS256 token with an unknown key id (kid) and an empty HMAC secret", header,
                        new byte[0]),
                new HmacSignature(HmacSignature.RANDOM_SECRET_DESCRIPTION, header,
                        RandomSecret.bytes())));
    }
}
