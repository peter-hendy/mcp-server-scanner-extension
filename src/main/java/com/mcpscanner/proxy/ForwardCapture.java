package com.mcpscanner.proxy;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

final class ForwardCapture implements AutoCloseable {

    private final List<Supplier<RecordedExchange>> exchanges = new ArrayList<>();
    private final Runnable onClose;

    ForwardCapture(Runnable onClose) {
        this.onClose = onClose;
    }

    synchronized void record(Supplier<RecordedExchange> exchange) {
        exchanges.add(exchange);
    }

    Optional<RecordedExchange> chosenFor(String forwardedBody) {
        List<RecordedExchange> recorded = snapshot();
        ForwardedMessage message = ForwardedMessage.of(forwardedBody);
        return lastOf(recorded.stream().filter(message::wasSentIn).toList()).or(() -> lastOf(recorded));
    }

    @Override
    public void close() {
        onClose.run();
    }

    private synchronized List<RecordedExchange> snapshot() {
        return exchanges.stream().map(Supplier::get).toList();
    }

    private static Optional<RecordedExchange> lastOf(List<RecordedExchange> exchanges) {
        return exchanges.isEmpty() ? Optional.empty() : Optional.of(exchanges.get(exchanges.size() - 1));
    }
}
