package com.mcpscanner.cvetargets.oauth;

import com.mcpscanner.auth.oauth.BrowserLauncher;

import java.io.IOException;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

public final class HeadlessBrowser implements BrowserLauncher {

    private static final int MAX_REDIRECTS = 10;
    private static final String CALLBACK_PATH = "/callback";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final int callbackPort;
    private final HttpClient client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .followRedirects(HttpClient.Redirect.NEVER)
            .cookieHandler(new CookieManager())
            .connectTimeout(TIMEOUT)
            .build();

    public HeadlessBrowser(int callbackPort) {
        this.callbackPort = callbackPort;
    }

    @Override
    public void open(URI authorizationUri) {
        Thread browsing = new Thread(() -> follow(authorizationUri), "cve-harness-headless-browser");
        browsing.setDaemon(true);
        browsing.start();
    }

    void follow(URI start) {
        URI current = start;
        for (int hop = 0; hop < MAX_REDIRECTS; hop++) {
            Optional<URI> next = get(current);
            if (next.isEmpty() || isCallback(current)) {
                return;
            }
            current = next.get();
        }
    }

    boolean isCallback(URI uri) {
        return uri.getPort() == callbackPort && "127.0.0.1".equals(uri.getHost()) && CALLBACK_PATH.equals(uri.getPath());
    }

    private Optional<URI> get(URI uri) {
        try {
            HttpResponse<Void> response = client.send(HttpRequest.newBuilder(uri).GET().timeout(TIMEOUT).build(),
                    HttpResponse.BodyHandlers.discarding());
            return response.headers().firstValue("location").map(uri::resolve);
        } catch (IOException e) {
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }
}
