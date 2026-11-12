package com.mcpscanner.cvetargets.environment;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;

public final class HttpDownloadSource implements DownloadSource {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration RESPONSE_TIMEOUT = Duration.ofMinutes(5);

    @Override
    public void retrieve(URI url, Path destination) throws IOException {
        HttpResponse<Path> response = send(HttpRequest.newBuilder(url).timeout(RESPONSE_TIMEOUT).GET().build(),
                destination);
        if (response.statusCode() / 100 != 2) {
            throw new IOException("HTTP " + response.statusCode() + " from " + response.uri());
        }
    }

    private static HttpClient client() {
        return HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
    }

    private HttpResponse<Path> send(HttpRequest request, Path destination) throws IOException {
        try {
            return client().send(request, HttpResponse.BodyHandlers.ofFile(destination,
                    StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("interrupted downloading " + request.uri());
        }
    }
}
