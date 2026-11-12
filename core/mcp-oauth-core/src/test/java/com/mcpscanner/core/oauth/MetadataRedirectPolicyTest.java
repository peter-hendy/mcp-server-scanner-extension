package com.mcpscanner.core.oauth;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

public final class MetadataRedirectPolicyTest {
    public static void main(String[] args) {
        followsRelativeRedirectAfterValidation();
        followsEverySupportedRedirectStatus();
        stopsAfterTwoRedirects();
        rejectsRedirectLoopsBeforeAnotherGet();
        rejectsUnsafeLocationsBeforeAnotherGet();
        rejectsUnvalidatedDestinationsBeforeGet();
        followsLoopbackHttpRedirects();
        rejectsNonLoopbackHttpHops();
    }

    private static void followsEverySupportedRedirectStatus() {
        for (int status : List.of(301, 302, 303, 307, 308)) {
            int[] requests = {0};
            var response = new MetadataRedirectPolicy().fetch(URI.create("https://issuer.example/meta"),
                    uri -> true,
                    uri -> ++requests[0] == 1
                            ? new MetadataRedirectPolicy.Response(status, "https://issuer.example/final", "")
                            : new MetadataRedirectPolicy.Response(200, null, "ok"));
            require(response.status() == 200 && requests[0] == 2, "Redirect status " + status + " followed");
        }
    }

    private static void stopsAfterTwoRedirects() {
        int[] requests = {0};
        var response = new MetadataRedirectPolicy().fetch(URI.create("https://issuer.example/meta"), uri -> true,
                uri -> ++requests[0] < 3
                        ? new MetadataRedirectPolicy.Response(308, "/hop-" + requests[0], "")
                        : new MetadataRedirectPolicy.Response(200, null, "done"));
        require(response.status() == 200 && requests[0] == 3, "At most two redirects and three GETs");
        expectFailure(() -> new MetadataRedirectPolicy().fetch(URI.create("https://issuer.example/meta"), uri -> true,
                uri -> new MetadataRedirectPolicy.Response(302, "/next", "")));
    }

    private static void rejectsRedirectLoopsBeforeAnotherGet() {
        int[] requests = {0};
        expectFailure(() -> new MetadataRedirectPolicy().fetch(URI.create("https://issuer.example/meta"), uri -> true,
                uri -> {
                    requests[0]++;
                    return new MetadataRedirectPolicy.Response(302, "/meta", "");
                }));
        require(requests[0] == 1, "Loop is rejected before repeated GET");
    }

    private static void rejectsUnsafeLocationsBeforeAnotherGet() {
        for (String location : List.of("/bad\\path", "/bad path", "/bad\npath", "https://user@issuer.example/x",
                "https://issuer.example/x#fragment", "http://issuer.example/x", "http://[invalid")) {
            int[] requests = {0};
            expectFailure(() -> new MetadataRedirectPolicy().fetch(URI.create("https://issuer.example/meta"), uri -> true,
                    uri -> {
                        requests[0]++;
                        return new MetadataRedirectPolicy.Response(302, location, "");
                    }));
            require(requests[0] == 1, "Unsafe location was not requested: " + location);
        }
    }

    private static void rejectsUnvalidatedDestinationsBeforeGet() {
        int[] requests = {0};
        int[] validations = {0};
        expectFailure(() -> new MetadataRedirectPolicy().fetch(URI.create("https://issuer.example/meta"), uri -> {
            validations[0]++;
            return validations[0] == 1;
        }, uri -> {
            requests[0]++;
            return new MetadataRedirectPolicy.Response(302, "/other", "");
        }));
        require(requests[0] == 1 && validations[0] == 2, "Rejected redirect destination is never fetched");
    }

    private static void followsRelativeRedirectAfterValidation() {
        var policy = new MetadataRedirectPolicy();
        var calls = new ArrayList<String>();
        var response = policy.fetch(URI.create("https://issuer.example/.well-known"),
                uri -> { calls.add("validate " + uri); return true; },
                uri -> {
                    calls.add("get " + uri);
                    return calls.size() == 2
                            ? new MetadataRedirectPolicy.Response(302, "metadata", "")
                            : new MetadataRedirectPolicy.Response(200, null, "document");
                });
        require(response.status() == 200 && response.body().equals("document"), "Final response returned");
        require(calls.equals(List.of("validate https://issuer.example/.well-known",
                "get https://issuer.example/.well-known", "validate https://issuer.example/metadata",
                "get https://issuer.example/metadata")), "Each destination validated before GET");
    }

    private static void followsLoopbackHttpRedirects() {
        var fetched = new ArrayList<URI>();
        var response = new MetadataRedirectPolicy().fetch(URI.create("http://127.0.0.1:8080/meta"), uri -> true, uri -> {
            fetched.add(uri);
            return fetched.size() == 1
                    ? new MetadataRedirectPolicy.Response(302, "http://localhost:8080/final", "")
                    : new MetadataRedirectPolicy.Response(200, null, "ok");
        });
        require(response.status() == 200 && fetched.equals(List.of(URI.create("http://127.0.0.1:8080/meta"),
                URI.create("http://localhost:8080/final"))), "Loopback HTTP hops followed");
    }

    private static void rejectsNonLoopbackHttpHops() {
        int[] requests = {0};
        expectFailure(() -> new MetadataRedirectPolicy().fetch(URI.create("http://issuer.example/meta"), uri -> true, uri -> {
            requests[0]++;
            return new MetadataRedirectPolicy.Response(200, null, "ok");
        }));
        for (String location : List.of("http://169.254.169.254/latest/meta-data/", "http://10.0.0.1/meta")) {
            expectFailure(() -> new MetadataRedirectPolicy().fetch(URI.create("http://localhost/meta"), uri -> true, uri -> {
                requests[0]++;
                return new MetadataRedirectPolicy.Response(302, location, "");
            }));
        }
        require(requests[0] == 2, "Non-loopback HTTP destinations are never requested");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void expectFailure(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("Expected redirect policy rejection");
        } catch (IllegalArgumentException expected) {
        }
    }
}
