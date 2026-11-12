package com.mcpscanner.cvetargets.oauth;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class HeadlessBrowserTest {

    @Test
    void followsRedirectsWithCookiesUntilItDeliversTheCallback() throws Exception {
        List<String> seen = new CopyOnWriteArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        int port = server.getAddress().getPort();
        server.createContext("/authorize", exchange -> {
            seen.add("authorize cookie=" + exchange.getRequestHeaders().getFirst("Cookie"));
            String target = exchange.getRequestHeaders().getFirst("Cookie") == null ? "/login" : "/callback?code=c1";
            exchange.getResponseHeaders().add("Location", target);
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/login", exchange -> {
            seen.add("login");
            exchange.getResponseHeaders().add("Set-Cookie", "session=s1; Path=/");
            exchange.getResponseHeaders().add("Location", "/authorize");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/callback", exchange -> {
            seen.add("callback " + exchange.getRequestURI().getQuery());
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        try {
            new HeadlessBrowser(port).follow(URI.create("http://127.0.0.1:" + port + "/authorize"));
        } finally {
            server.stop(0);
        }

        assertThat(seen).containsExactly("authorize cookie=null", "login", "authorize cookie=session=s1",
                "callback code=c1");
    }

    @Test
    void recognisesOnlyItsOwnLoopbackCallback() {
        HeadlessBrowser browser = new HeadlessBrowser(4321);

        assertThat(browser.isCallback(URI.create("http://127.0.0.1:4321/callback"))).isTrue();
        assertThat(browser.isCallback(URI.create("http://127.0.0.1:4322/callback"))).isFalse();
        assertThat(browser.isCallback(URI.create("http://example.com:4321/callback"))).isFalse();
        assertThat(browser.isCallback(URI.create("http://127.0.0.1:4321/authorize"))).isFalse();
    }
}
