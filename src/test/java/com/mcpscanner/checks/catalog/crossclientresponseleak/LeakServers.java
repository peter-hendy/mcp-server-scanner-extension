package com.mcpscanner.checks.catalog.crossclientresponseleak;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.mcpscanner.client.ProtocolHttpRequest;
import com.mcpscanner.client.ProtocolHttpResponse;
import com.mcpscanner.mcp.McpObjectMapper;
import com.mcpscanner.testutil.FakeMcpServer;
import com.mcpscanner.testutil.ScriptedBurpHttp;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntPredicate;
import java.util.function.UnaryOperator;

final class LeakServers {

    static final String URL = "http://target.test/mcp";
    static final String ECHOED_ARGUMENT = "message";
    static final String PROGRESS_TOKEN = "mcp-scanner-progress";

    private static final String MODERN_VERSION = "2026-07-28";
    private static final String PROTOCOL_VERSION_META = "io.modelcontextprotocol/protocolVersion";

    private static final String BUSY = "busy";
    private static final String RATE_LIMITED = "rate limited";
    private static final String RATE_LIMITED_FOR_A_MINUTE = "rate limited for a minute";
    private static final String NO_RESPONSE = "no response";
    private static final Duration COMPANION_WAIT = Duration.ofSeconds(5);
    private static final Duration ROBBED_OWNER_WAIT = Duration.ofMillis(300);

    private LeakServers() {
    }

    static Server echoing() {
        return new Server((own, id, gate) -> "echo:" + own);
    }

    static Server notEchoing() {
        return new Server((own, id, gate) -> "done");
    }

    static Server misroutingByRequestId() {
        return misroutingCollidingIds(LeakServers::echo, batch -> true);
    }

    static Server misroutingCollidingIdsAndAnsweringDistinctIdsWithoutMarkers() {
        return misroutingCollidingIds(own -> "done", batch -> true);
    }

    static Server misroutingOnlyEverySecondCollidingBatch() {
        List<Integer> collidingBatches = Collections.synchronizedList(new ArrayList<>());
        return misroutingCollidingIds(LeakServers::echo, batch -> {
            synchronized (collidingBatches) {
                if (!collidingBatches.contains(batch)) {
                    collidingBatches.add(batch);
                }
                return collidingBatches.indexOf(batch) % 2 == 1;
            }
        });
    }

    static Server overwritingOneSharedRecordForCollidingIds() {
        AtomicReference<String> record = new AtomicReference<>();
        IdCollisions collisions = new IdCollisions();
        return new Server((own, id, gate) -> {
            collisions.arrive(id);
            record.set(own);
            gate.awaitRoundCompanions();
            try {
                return echo(collisions.collided(id) ? record.get() : own);
            } finally {
                collisions.leave(id);
            }
        });
    }

    private static Server misroutingCollidingIds(UnaryOperator<String> distinctIdReply,
                                                 IntPredicate misroutesBatch) {
        Map<Long, AtomicReference<ReplySlot>> latestCallerById = new ConcurrentHashMap<>();
        IdCollisions collisions = new IdCollisions();
        return new Server((own, id, gate) -> {
            ReplySlot mine = new ReplySlot();
            AtomicReference<ReplySlot> latest = latestCallerById.computeIfAbsent(id, key -> new AtomicReference<>());
            latest.set(mine);
            collisions.arrive(id);
            gate.awaitRoundCompanions();
            try {
                if (!collisions.collided(id)) {
                    return gate.racing() ? distinctIdReply.apply(own) : echo(own);
                }
                if (!misroutesBatch.test(gate.batch())) {
                    return echo(own);
                }
                if (latest.get() != mine) {
                    latest.get().deliver(echo(own));
                }
                return mine.awaitReply();
            } finally {
                collisions.leave(id);
            }
        });
    }

    static Server rotatingRepliesAmongConcurrentCallers() {
        RotatingBatch batch = new RotatingBatch();
        return new Server((own, id, gate) -> "echo:" + batch.previousCaller(own, gate));
    }

    static Server rotatingOnlyTheFirstRace(boolean failUnderLoadAfterwards) {
        RotatingBatch batch = new RotatingBatch();
        AtomicBoolean rotated = new AtomicBoolean();
        return new Server((own, id, gate) -> {
            if (!gate.racing()) {
                return "echo:" + own;
            }
            if (rotated.get()) {
                return failUnderLoadAfterwards ? BUSY : "echo:" + own;
            }
            String reply = batch.previousCaller(own, gate);
            rotated.set(true);
            return "echo:" + reply;
        });
    }

    static Server rateLimitingTheRace() {
        return new Server((own, id, gate) -> gate.racing() ? RATE_LIMITED : "echo:" + own);
    }

    static Server rateLimitingTheRaceForAMinute() {
        return new Server((own, id, gate) -> gate.racing() ? RATE_LIMITED_FOR_A_MINUTE : "echo:" + own);
    }

    static Server refusingTheRace() {
        return new Server((own, id, gate) -> gate.racing() ? BUSY : "echo:" + own);
    }

    static Server stallingTheRace(Duration stall) {
        return new Server((own, id, gate) -> {
            if (gate.racing()) {
                gate.stallUntilReleased(stall);
            }
            return "echo:" + own;
        });
    }

    static Server echoingOnlyTheFirstCall() {
        AtomicInteger calls = new AtomicInteger();
        return new Server((own, id, gate) -> calls.incrementAndGet() == 1 ? "echo:" + own : "done");
    }

    static Server rememberingEveryCaller() {
        List<String> history = Collections.synchronizedList(new ArrayList<>());
        return new Server((own, id, gate) -> {
            history.add(own);
            return "notes:" + String.join(",", history);
        });
    }

    static Server refusingHandshakes() {
        return echoing().acceptingHandshakes(0);
    }

    static Server rateLimitingHandshakes() {
        return echoing().acceptingHandshakes(0).refusingHandshakesWith(429);
    }

    static HttpRequestResponse toolCall(String arguments) {
        return toolCall(URL, arguments);
    }

    static HttpRequestResponse toolCall(String url, String arguments) {
        HttpRequest request = HttpRequest.httpRequestFromUrl(url).withMethod("POST")
                .withHeader("Content-Type", "application/json")
                .withHeader("MCP-Protocol-Version", FakeMcpServer.LEGACY_VERSION)
                .withBody("{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"tools/call\",\"params\":{\"name\":\"note\","
                        + "\"arguments\":" + arguments + "}}");
        return HttpRequestResponse.httpRequestResponse(request, null);
    }

    static HttpRequestResponse modernToolCall(String arguments) {
        HttpRequest request = HttpRequest.httpRequestFromUrl(URL).withMethod("POST")
                .withHeader("Content-Type", "application/json")
                .withHeader("MCP-Protocol-Version", MODERN_VERSION)
                .withBody("{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"tools/call\",\"params\":{\"name\":\"note\","
                        + "\"arguments\":" + arguments + ",\"_meta\":{\"" + PROTOCOL_VERSION_META + "\":\""
                        + MODERN_VERSION + "\",\"io.modelcontextprotocol/clientInfo\":{\"name\":\"scanner\","
                        + "\"version\":\"1\"},\"io.modelcontextprotocol/clientCapabilities\":{},"
                        + "\"progressToken\":\"" + PROGRESS_TOKEN + "\"}}}");
        return HttpRequestResponse.httpRequestResponse(request, null);
    }

    static HttpRequestResponse modernEchoableToolCall() {
        return modernToolCall("{\"" + ECHOED_ARGUMENT + "\":\"hello\"}");
    }

    static String markerIn(String requestBody) {
        return parse(requestBody).path("params").path("arguments").path(ECHOED_ARGUMENT).asText();
    }

    static HttpRequestResponse echoableToolCall() {
        return toolCall("{\"" + ECHOED_ARGUMENT + "\":\"hello\"}");
    }

    @FunctionalInterface
    interface ToolBehaviour {
        String reply(String own, long requestId, RaceGate gate);
    }

    static final class RaceGate {

        private final AtomicInteger openSessions = new AtomicInteger();
        private final AtomicInteger batches = new AtomicInteger();
        private final CyclicBarrier round = new CyclicBarrier(LeakTrial.CLIENTS, batches::incrementAndGet);
        private final CountDownLatch released = new CountDownLatch(1);

        boolean racing() {
            return openSessions.get() >= LeakTrial.CLIENTS;
        }

        void awaitRoundCompanions() {
            if (!racing()) {
                return;
            }
            try {
                round.await(COMPANION_WAIT.toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } catch (BrokenBarrierException | TimeoutException incompleteRound) {
                round.reset();
            }
        }

        int batch() {
            return batches.get();
        }

        void stallUntilReleased(Duration cap) {
            try {
                released.await(cap.toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }

        private void release() {
            released.countDown();
        }

        private void sessionOpened() {
            openSessions.incrementAndGet();
        }

        private void sessionClosed() {
            openSessions.decrementAndGet();
        }
    }

    static final class Server implements AutoCloseable {

        final ScriptedBurpHttp http;
        private final List<String> toolCallArguments = Collections.synchronizedList(new ArrayList<>());
        private final RaceGate gate = new RaceGate();
        private final AtomicInteger handshakes = new AtomicInteger();
        private final AtomicInteger deletes = new AtomicInteger();
        private volatile int acceptedHandshakes = Integer.MAX_VALUE;
        private volatile int handshakeRefusal = 403;

        Server(ToolBehaviour behaviour) {
            this.http = new ScriptedBurpHttp(request -> respond(request, behaviour));
        }

        Server acceptingHandshakes(int limit) {
            acceptedHandshakes = limit;
            return this;
        }

        Server refusingHandshakesWith(int status) {
            handshakeRefusal = status;
            return this;
        }

        @Override
        public void close() {
            gate.release();
        }

        int sessionsOpened() {
            return handshakes.get();
        }

        int sessionsDeleted() {
            return deletes.get();
        }

        int requestsSeen() {
            return toolCallArguments.size();
        }

        private Optional<ProtocolHttpResponse> respond(ProtocolHttpRequest request, ToolBehaviour behaviour) {
            if ("DELETE".equals(request.method())) {
                deletes.incrementAndGet();
                gate.sessionClosed();
            }
            String method = FakeMcpServer.method(request);
            if ("initialize".equals(method)) {
                return Optional.of(handshake(request));
            }
            if (!"tools/call".equals(method)) {
                return Optional.of(FakeMcpServer.respond(request));
            }
            JsonNode envelope = parse(request.body());
            if (framedForAnotherVersion(request, envelope)) {
                return Optional.of(FakeMcpServer.exchange(request, 400, Map.of(), "protocol version mismatch"));
            }
            String own = envelope.path("params").path("arguments").path(ECHOED_ARGUMENT).asText("");
            toolCallArguments.add(own);
            return toolReply(request, envelope, behaviour.reply(own, envelope.path("id").asLong(), gate));
        }

        private static boolean framedForAnotherVersion(ProtocolHttpRequest request, JsonNode envelope) {
            JsonNode framedVersion = envelope.path("params").path("_meta").path(PROTOCOL_VERSION_META);
            return !framedVersion.isMissingNode() && !framedVersion.asText().equals(headerVersion(request));
        }

        private static String headerVersion(ProtocolHttpRequest request) {
            return request.headers().entrySet().stream()
                    .filter(header -> header.getKey().equalsIgnoreCase("MCP-Protocol-Version"))
                    .map(Map.Entry::getValue).findFirst().orElse("");
        }

        private ProtocolHttpResponse handshake(ProtocolHttpRequest request) {
            if (handshakes.incrementAndGet() > acceptedHandshakes) {
                handshakes.decrementAndGet();
                return FakeMcpServer.exchange(request, handshakeRefusal, Map.of(), "forbidden");
            }
            gate.sessionOpened();
            return FakeMcpServer.respond(request);
        }

        private static Optional<ProtocolHttpResponse> toolReply(ProtocolHttpRequest request, JsonNode envelope,
                                                                String reply) {
            return switch (reply) {
                case NO_RESPONSE -> Optional.empty();
                case BUSY -> Optional.of(FakeMcpServer.exchange(request, 503, Map.of(), BUSY));
                case RATE_LIMITED -> Optional.of(FakeMcpServer.exchange(request, 429, Map.of(), RATE_LIMITED));
                case RATE_LIMITED_FOR_A_MINUTE -> Optional.of(FakeMcpServer.exchange(request, 429,
                        Map.of("Retry-After", List.of("60")), RATE_LIMITED));
                default -> Optional.of(FakeMcpServer.exchange(request, 200,
                        Map.of("Content-Type", List.of("application/json")),
                        "{\"jsonrpc\":\"2.0\",\"id\":" + envelope.path("id") + ",\"result\":{\"content\":"
                                + "[{\"type\":\"text\",\"text\":"
                                + McpObjectMapper.INSTANCE.valueToTree(reply) + "}]}}"));
            };
        }
    }

    private static final class RotatingBatch {

        private final List<String> callers = new ArrayList<>();
        private final AtomicInteger inFlight = new AtomicInteger();

        String previousCaller(String own, RaceGate gate) {
            inFlight.incrementAndGet();
            synchronized (callers) {
                callers.add(own);
            }
            gate.awaitRoundCompanions();
            String previous;
            synchronized (callers) {
                int index = callers.indexOf(own);
                previous = callers.get((index + callers.size() - 1) % callers.size());
            }
            if (inFlight.decrementAndGet() == 0) {
                synchronized (callers) {
                    callers.clear();
                }
            }
            return previous;
        }
    }

    private static String echo(String text) {
        return "echo:" + text;
    }

    private static final class IdCollisions {

        private final Map<Long, AtomicInteger> callersById = new ConcurrentHashMap<>();

        void arrive(long id) {
            callersById.computeIfAbsent(id, key -> new AtomicInteger()).incrementAndGet();
        }

        boolean collided(long id) {
            return callersById.get(id).get() > 1;
        }

        void leave(long id) {
            callersById.get(id).decrementAndGet();
        }
    }

    private static JsonNode parse(String body) {
        try {
            return McpObjectMapper.INSTANCE.readTree(body);
        } catch (IOException invalid) {
            throw new UncheckedIOException(invalid);
        }
    }

    private static final class ReplySlot {

        private final CompletableFuture<String> reply = new CompletableFuture<>();

        void deliver(String text) {
            reply.complete(text);
        }

        String awaitReply() {
            try {
                return reply.get(ROBBED_OWNER_WAIT.toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return NO_RESPONSE;
            } catch (ExecutionException | TimeoutException robbed) {
                return NO_RESPONSE;
            }
        }
    }
}
