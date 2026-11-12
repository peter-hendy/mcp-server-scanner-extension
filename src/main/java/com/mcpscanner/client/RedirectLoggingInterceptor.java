package com.mcpscanner.client;

import com.mcpscanner.config.RedirectMode;
import com.mcpscanner.logging.McpEventLog;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public final class RedirectLoggingInterceptor {

    private final RedirectPolicy policy;

    public RedirectLoggingInterceptor(McpEventLog eventLog) {
        this(eventLog, () -> RedirectMode.METHOD_PRESERVING_SAME_ORIGIN);
    }

    public RedirectLoggingInterceptor(McpEventLog eventLog, Supplier<RedirectMode> redirectMode) {
        this(new RedirectPolicy(eventLog, redirectMode));
    }

    public RedirectLoggingInterceptor(RedirectPolicy policy) {
        this.policy = policy;
    }

    public void setRedirectModeSupplier(Supplier<RedirectMode> redirectMode) {
        policy.setModeSupplier(redirectMode);
    }

    public RedirectMode mode() {
        return policy.mode();
    }

    public <T> HttpResponse<T> sendAndLog(HttpClient client, HttpRequest request,
                                          HttpResponse.BodyHandler<T> bodyHandler)
            throws IOException, InterruptedException {
        HttpRequest current = request;
        HttpResponse<T> response = client.send(current, bodyHandler);
        for (int hop = 0; hop < RedirectPolicy.MAX_REDIRECTS; hop++) {
            Optional<RedirectHop> next = nextHop(current, response);
            if (next.isEmpty()) break;
            closeBody(response.body());
            current = redirectedRequest(current, next.get());
            response = client.send(current, bodyHandler);
        }
        policy.warnIfCrossOriginFollowed(request.uri(), response.uri());
        return response;
    }

    public <T> CompletableFuture<HttpResponse<T>> sendAsyncAndLog(HttpClient client, HttpRequest request,
                                                                   HttpResponse.BodyHandler<T> bodyHandler) {
        AtomicReference<CompletableFuture<HttpResponse<T>>> active = new AtomicReference<>();
        CompletableFuture<HttpResponse<T>> outcome = new CompletableFuture<>();
        sendAsync(client, request, bodyHandler, 0, active).whenComplete((response, failure) -> {
            if (failure != null) outcome.completeExceptionally(failure);
            else {
                policy.warnIfCrossOriginFollowed(request.uri(), response.uri());
                outcome.complete(response);
            }
        });
        outcome.whenComplete((response, failure) -> {
            if (outcome.isCancelled()) active.get().cancel(true);
        });
        return outcome;
    }

    private <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpClient client, HttpRequest request,
                                                              HttpResponse.BodyHandler<T> bodyHandler, int redirects,
                                                              AtomicReference<CompletableFuture<HttpResponse<T>>> active) {
        CompletableFuture<HttpResponse<T>> sent = client.sendAsync(request, bodyHandler);
        active.set(sent);
        return sent.thenCompose(response -> {
            if (redirects == RedirectPolicy.MAX_REDIRECTS) return CompletableFuture.completedFuture(response);
            Optional<RedirectHop> next = nextHop(request, response);
            if (next.isEmpty()) return CompletableFuture.completedFuture(response);
            closeBody(response.body());
            return sendAsync(client, redirectedRequest(request, next.get()), bodyHandler, redirects + 1, active);
        });
    }

    private <T> Optional<RedirectHop> nextHop(HttpRequest request, HttpResponse<T> response) {
        if (!RedirectPolicy.isRedirect(response.statusCode())) return Optional.empty();
        return policy.nextHop(response.statusCode(), request.method(), request.uri(),
                response.headers().firstValue("Location"));
    }

    private static HttpRequest redirectedRequest(HttpRequest request, RedirectHop hop) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(request, (name, value) -> hop.keepsHeader(name));
        builder.uri(hop.target());
        if (hop.switchesToGet()) builder.GET();
        return builder.build();
    }

    private static void closeBody(Object body) {
        if (body instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception ignored) {
            }
        }
    }
}
