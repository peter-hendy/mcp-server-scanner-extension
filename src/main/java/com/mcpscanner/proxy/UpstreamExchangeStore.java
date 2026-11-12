package com.mcpscanner.proxy;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.responses.HttpResponse;
import com.mcpscanner.http.UpstreamExchanges;
import com.mcpscanner.mcp.ScannerSentinels;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class UpstreamExchangeStore implements UpstreamExchanges {

    static final int MAX_EXCHANGES = 4_096;
    static final long MAX_BODY_BYTES = 32L * 1024 * 1024;
    private static final int ID_BYTES = 16;

    private final int maxExchanges;
    private final long maxBodyBytes;
    private final SecureRandom random = new SecureRandom();
    private final ExchangeEvidence evidence = new ExchangeEvidence();
    private final Map<String, RecordedExchange> exchanges = new LinkedHashMap<>();
    private long bodyBytes;

    public UpstreamExchangeStore() {
        this(MAX_EXCHANGES, MAX_BODY_BYTES);
    }

    UpstreamExchangeStore(int maxExchanges, long maxBodyBytes) {
        this.maxExchanges = maxExchanges;
        this.maxBodyBytes = maxBodyBytes;
    }

    synchronized String put(RecordedExchange exchange) {
        String id = newId();
        exchanges.put(id, exchange);
        bodyBytes += exchange.bodyBytes();
        evictOverflow();
        return id;
    }

    @Override
    public Optional<HttpRequestResponse> upstreamOf(HttpRequestResponse proxied) {
        return exchangeIdOf(proxied).flatMap(this::find).map(evidence::of);
    }

    private static Optional<String> exchangeIdOf(HttpRequestResponse proxied) {
        HttpResponse response = proxied.response();
        return response == null ? Optional.empty()
                : Optional.ofNullable(response.headerValue(ScannerSentinels.EXCHANGE_HEADER));
    }

    private synchronized Optional<RecordedExchange> find(String id) {
        return Optional.ofNullable(exchanges.get(id));
    }

    private void evictOverflow() {
        Iterator<RecordedExchange> oldestFirst = exchanges.values().iterator();
        while (oldestFirst.hasNext() && (exchanges.size() > maxExchanges || bodyBytes > maxBodyBytes)) {
            bodyBytes -= oldestFirst.next().bodyBytes();
            oldestFirst.remove();
        }
    }

    private String newId() {
        byte[] id = new byte[ID_BYTES];
        random.nextBytes(id);
        return HexFormat.of().formatHex(id);
    }
}
