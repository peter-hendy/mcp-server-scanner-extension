package com.mcpscanner.testutil;

import com.mcpscanner.checks.shared.collaborator.InteractionWindow;
import com.mcpscanner.checks.shared.collaborator.InteractionWindowHint;

import burp.api.montoya.collaborator.CollaboratorClient;
import burp.api.montoya.collaborator.CollaboratorPayload;
import burp.api.montoya.collaborator.CollaboratorServer;
import burp.api.montoya.collaborator.DnsDetails;
import burp.api.montoya.collaborator.HttpDetails;
import burp.api.montoya.collaborator.Interaction;
import burp.api.montoya.collaborator.InteractionFilter;
import burp.api.montoya.collaborator.InteractionId;
import burp.api.montoya.collaborator.InteractionType;
import burp.api.montoya.collaborator.PayloadOption;
import burp.api.montoya.collaborator.SecretKey;
import burp.api.montoya.collaborator.SmtpDetails;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

public final class LoopbackCollaborator implements CollaboratorClient, InteractionWindowHint, AutoCloseable {

    private static final String LOOPBACK_DOMAIN = ".localhost";
    private static final int ID_BYTES = 10;
    private static final InteractionWindow LOCAL_WINDOW =
            new InteractionWindow(Duration.ofMillis(400), Duration.ofMillis(100));

    private final HttpServer server;
    private final SecureRandom random = new SecureRandom();
    private final List<Interaction> interactions = new CopyOnWriteArrayList<>();

    private LoopbackCollaborator(HttpServer server) {
        this.server = server;
    }

    public static LoopbackCollaborator start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        LoopbackCollaborator collaborator = new LoopbackCollaborator(server);
        server.createContext("/", collaborator::record);
        server.start();
        return collaborator;
    }

    @Override
    public CollaboratorPayload generatePayload(PayloadOption... options) {
        String id = HexFormat.of().formatHex(random.generateSeed(ID_BYTES));
        return new Payload(new Id(id), id + LOOPBACK_DOMAIN + ":" + server.getAddress().getPort());
    }

    @Override
    public CollaboratorPayload generatePayload(String customData, PayloadOption... options) {
        return generatePayload(options);
    }

    @Override
    public List<Interaction> getAllInteractions() {
        return List.copyOf(interactions);
    }

    @Override
    public List<Interaction> getInteractions(InteractionFilter filter) {
        return interactions.stream().filter(interaction -> filter.matches(null, interaction)).toList();
    }

    @Override
    public CollaboratorServer server() {
        throw new UnsupportedOperationException("loopback stand-in has no Collaborator server");
    }

    @Override
    public SecretKey getSecretKey() {
        throw new UnsupportedOperationException("loopback stand-in has no secret key");
    }

    @Override
    public InteractionWindow interactionWindow() {
        return LOCAL_WINDOW;
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private void record(HttpExchange exchange) throws IOException {
        String host = String.valueOf(exchange.getRequestHeaders().getFirst("Host")).toLowerCase(Locale.ROOT);
        int domain = host.indexOf(LOOPBACK_DOMAIN);
        if (domain > 0) {
            interactions.add(new HttpInteraction(new Id(host.substring(0, domain)),
                    exchange.getRemoteAddress().getAddress(), exchange.getRemoteAddress().getPort()));
        }
        exchange.sendResponseHeaders(204, -1);
        exchange.close();
    }

    private record Id(String value) implements InteractionId {
        @Override
        public String toString() {
            return value;
        }
    }

    private record Payload(InteractionId id, String host) implements CollaboratorPayload {
        @Override
        public Optional<String> customData() {
            return Optional.empty();
        }

        @Override
        public Optional<CollaboratorServer> server() {
            return Optional.empty();
        }

        @Override
        public String toString() {
            return host;
        }
    }

    private record HttpInteraction(InteractionId id, InetAddress clientIp, int clientPort) implements Interaction {
        @Override
        public InteractionType type() {
            return InteractionType.HTTP;
        }

        @Override
        public ZonedDateTime timeStamp() {
            return ZonedDateTime.now();
        }

        @Override
        public Optional<DnsDetails> dnsDetails() {
            return Optional.empty();
        }

        @Override
        public Optional<HttpDetails> httpDetails() {
            return Optional.empty();
        }

        @Override
        public Optional<SmtpDetails> smtpDetails() {
            return Optional.empty();
        }

        @Override
        public Optional<String> customData() {
            return Optional.empty();
        }
    }
}
