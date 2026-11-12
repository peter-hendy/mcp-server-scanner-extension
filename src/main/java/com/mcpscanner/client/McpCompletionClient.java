package com.mcpscanner.client;

import com.mcpscanner.core.protocol.McpResponses.Reply;

import java.util.List;
import java.util.Map;

public class McpCompletionClient {

    private final McpScannerSession session;

    public McpCompletionClient(McpScannerSession session) {
        this.session = session;
    }

    public CompletionOutcome complete(CompletionRequest request) {
        try {
            return outcomeOf(session.call(CompletionRequest.METHOD, request.paramsJson()));
        } catch (McpHttpStatusException refused) {
            return new CompletionOutcome.Failed("HTTP " + refused.status());
        } catch (RuntimeException failure) {
            return new CompletionOutcome.Failed(reasonOf(failure));
        }
    }

    private static CompletionOutcome outcomeOf(Reply reply) {
        if (reply == null) {
            return invalid("no reply");
        }
        if (reply.error() != null && !reply.error().isEmpty()) {
            return new CompletionOutcome.Failed(JsonRpcErrorText.of(reply.error()));
        }
        Map<?, ?> completion = completionOf(reply);
        if (!(completion.get("values") instanceof List<?> values)) {
            return invalid("no completion values");
        }
        return new CompletionOutcome.Values(textValues(values), totalOf(completion.get("total")),
                completion.get("hasMore") instanceof Boolean hasMore ? hasMore : null);
    }

    private static List<String> textValues(List<?> values) {
        return values.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .toList();
    }

    private static Integer totalOf(Object total) {
        if (total instanceof Integer whole && whole >= 0) {
            return whole;
        }
        if (total instanceof Long whole && whole >= 0) {
            return (int) Math.min(whole, Integer.MAX_VALUE);
        }
        return null;
    }

    private static Map<?, ?> completionOf(Reply reply) {
        return reply.result() != null && reply.result().get("completion") instanceof Map<?, ?> completion
                ? completion : Map.of();
    }

    private static String reasonOf(RuntimeException failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }

    private static CompletionOutcome invalid(String reason) {
        return new CompletionOutcome.Failed("Invalid reply: " + reason);
    }
}
