package com.mcpscanner.ui.sendto;

import burp.api.montoya.http.message.requests.HttpRequest;

import java.util.function.BiConsumer;

public interface SendToDestination {

    String name();

    void send(HttpRequest request, String title);

    static SendToDestination of(String name, BiConsumer<HttpRequest, String> sender) {
        return new SendToDestination() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public void send(HttpRequest request, String title) {
                sender.accept(request, title);
            }
        };
    }
}
