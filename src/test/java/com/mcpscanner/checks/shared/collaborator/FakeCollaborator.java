package com.mcpscanner.checks.shared.collaborator;

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
import burp.api.montoya.http.message.HttpRequestResponse;

import java.net.InetAddress;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

public class FakeCollaborator implements CollaboratorClient {

    public static final String DOMAIN = "oob.fake.test";

    private final AtomicInteger payloadCounter = new AtomicInteger();
    private final List<Interaction> pending = new ArrayList<>();
    private int pollCount;

    public synchronized void interact(CollaboratorPayload payload, InteractionType type) {
        pending.add(interaction(payload.id().toString(), type, null));
    }

    public synchronized void interactOverHttp(CollaboratorPayload payload, HttpRequestResponse request) {
        pending.add(interaction(payload.id().toString(), InteractionType.HTTP, request));
    }

    public synchronized void interactWithUnknownPayload(String id) {
        pending.add(interaction(id, InteractionType.DNS, null));
    }

    public synchronized int pollCount() {
        return pollCount;
    }

    @Override
    public CollaboratorPayload generatePayload(PayloadOption... options) {
        String id = "p" + payloadCounter.incrementAndGet() + "x";
        return payload(id);
    }

    @Override
    public CollaboratorPayload generatePayload(String customData, PayloadOption... options) {
        return generatePayload(options);
    }

    @Override
    public synchronized List<Interaction> getAllInteractions() {
        pollCount++;
        List<Interaction> drained = List.copyOf(pending);
        pending.clear();
        return drained;
    }

    @Override
    public List<Interaction> getInteractions(InteractionFilter filter) {
        throw new UnsupportedOperationException("filtered polling drains interactions of other payloads");
    }

    @Override
    public CollaboratorServer server() {
        return null;
    }

    @Override
    public SecretKey getSecretKey() {
        return null;
    }

    public static CollaboratorPayload payload(String id) {
        InteractionId interactionId = idOf(id);
        return new CollaboratorPayload() {
            @Override
            public InteractionId id() {
                return interactionId;
            }

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
                return id + "." + DOMAIN;
            }
        };
    }

    public static Interaction interaction(String id, InteractionType type, HttpRequestResponse request) {
        InteractionId interactionId = idOf(id);
        return new Interaction() {
            @Override
            public InteractionId id() {
                return interactionId;
            }

            @Override
            public InteractionType type() {
                return type;
            }

            @Override
            public ZonedDateTime timeStamp() {
                return ZonedDateTime.parse("2026-11-06T12:00:00Z");
            }

            @Override
            public InetAddress clientIp() {
                return InetAddress.getLoopbackAddress();
            }

            @Override
            public int clientPort() {
                return 40000;
            }

            @Override
            public Optional<DnsDetails> dnsDetails() {
                return Optional.empty();
            }

            @Override
            public Optional<HttpDetails> httpDetails() {
                if (request == null) {
                    return Optional.empty();
                }
                return Optional.of(new HttpDetails() {
                    @Override
                    public burp.api.montoya.http.HttpProtocol protocol() {
                        return burp.api.montoya.http.HttpProtocol.HTTP;
                    }

                    @Override
                    public HttpRequestResponse requestResponse() {
                        return request;
                    }
                });
            }

            @Override
            public Optional<SmtpDetails> smtpDetails() {
                return Optional.empty();
            }

            @Override
            public Optional<String> customData() {
                return Optional.empty();
            }
        };
    }

    private static InteractionId idOf(String id) {
        return new InteractionId() {
            @Override
            public String toString() {
                return id;
            }
        };
    }
}
