package com.mcpscanner.client;

import com.mcpscanner.http.ThreadTallies;
import com.mcpscanner.logging.McpEventLog;

import java.util.Objects;
import java.util.function.Supplier;

final class OperationRequests {

    private final McpEventLog eventLog;
    private final ThreadTallies tallies = new ThreadTallies();

    OperationRequests(McpEventLog eventLog) {
        this.eventLog = Objects.requireNonNull(eventLog);
    }

    ProtocolHttpTransport counting(ProtocolHttpTransport transport) {
        return (request, timeout) -> {
            tallies.record();
            return transport.send(request, timeout);
        };
    }

    void measure(String operation, Runnable work) {
        measure(operation, () -> {
            work.run();
            return null;
        });
    }

    <T> T measure(String operation, Supplier<T> work) {
        return tallies.measure(operation, eventLog::debug, work);
    }
}
