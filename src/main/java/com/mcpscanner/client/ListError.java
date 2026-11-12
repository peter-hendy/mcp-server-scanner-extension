package com.mcpscanner.client;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

public sealed interface ListError {

    int METHOD_NOT_FOUND = -32601;

    String describe();

    default boolean isMethodNotFound() {
        return false;
    }

    static ListError from(RuntimeException failure) {
        if (failure instanceof McpHttpStatusException http) {
            return new HttpStatus(http.status());
        }
        return new TransportFailure(deepestReason(failure));
    }

    static ListError fromJsonRpc(Map<String, Object> error) {
        Object code = error.get("code");
        Object message = error.get("message");
        return new JsonRpcError(code instanceof Number number ? number.intValue() : 0,
                message == null ? "" : message.toString());
    }

    private static String deepestReason(Throwable failure) {
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable root = failure;
        String reason = null;
        for (Throwable current = failure; current != null && visited.add(current); current = current.getCause()) {
            root = current;
            if (current.getMessage() != null && !current.getMessage().isBlank()) {
                reason = current.getMessage();
            }
        }
        return reason != null ? reason : root.getClass().getSimpleName();
    }

    record JsonRpcError(int code, String message) implements ListError {
        public String describe() {
            return "JSON-RPC error " + code + ": " + message;
        }

        @Override
        public boolean isMethodNotFound() {
            return code == METHOD_NOT_FOUND;
        }
    }

    record HttpStatus(int status) implements ListError {
        public String describe() {
            return "HTTP " + status;
        }
    }

    record TransportFailure(String reason) implements ListError {
        public String describe() {
            return "transport failure: " + reason;
        }
    }

    record InvalidReply(String reason) implements ListError {
        public String describe() {
            return "invalid reply: " + reason;
        }
    }
}
