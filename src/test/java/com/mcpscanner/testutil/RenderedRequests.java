package com.mcpscanner.testutil;

import burp.api.montoya.http.HttpService;
import burp.api.montoya.internal.ObjectFactoryLocator;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

public final class RenderedRequests {

    private final List<HttpService> services = new CopyOnWriteArrayList<>();
    private final List<String> raw = new CopyOnWriteArrayList<>();

    private RenderedRequests() {
    }

    public static RenderedRequests capture() {
        RenderedRequests rendered = new RenderedRequests();
        when(ObjectFactoryLocator.FACTORY.httpRequest(any(HttpService.class), anyString())).thenAnswer(call -> {
            rendered.services.add(call.getArgument(0));
            rendered.raw.add(call.getArgument(1));
            return RawHttpRequestStub.fromRaw(call.getArgument(1));
        });
        return rendered;
    }

    public List<HttpService> services() {
        return List.copyOf(services);
    }

    public List<String> raw() {
        return List.copyOf(raw);
    }
}
