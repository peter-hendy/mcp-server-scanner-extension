package com.mcpscanner.proxy.observe;

import burp.api.montoya.http.handler.HttpRequestToBeSent;
import burp.api.montoya.http.handler.HttpResponseReceived;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class ToggleAwareBurpTrafficObserverTest {

    private final BurpTrafficObserver delegate = mock(BurpTrafficObserver.class);
    private final HttpRequestToBeSent request = mock(HttpRequestToBeSent.class);
    private final HttpResponseReceived response = mock(HttpResponseReceived.class);

    @Test
    void offDoesNotTouchTheDelegateOnRequests() {
        BurpTrafficObserver observer = new ToggleAwareBurpTrafficObserver(() -> false, delegate);

        observer.observeRequest(request);

        verifyNoInteractions(delegate);
    }

    @Test
    void offDoesNotTouchTheDelegateOnResponses() {
        BurpTrafficObserver observer = new ToggleAwareBurpTrafficObserver(() -> false, delegate);

        observer.observeResponse(response);

        verifyNoInteractions(delegate);
    }

    @Test
    void onDelegatesRequests() {
        BurpTrafficObserver observer = new ToggleAwareBurpTrafficObserver(() -> true, delegate);

        observer.observeRequest(request);

        verify(delegate).observeRequest(request);
    }

    @Test
    void onDelegatesResponses() {
        BurpTrafficObserver observer = new ToggleAwareBurpTrafficObserver(() -> true, delegate);

        observer.observeResponse(response);

        verify(delegate).observeResponse(response);
    }

    @Test
    void readsTheSupplierLiveOnEachCall() {
        AtomicBoolean enabled = new AtomicBoolean(false);
        BurpTrafficObserver observer = new ToggleAwareBurpTrafficObserver(enabled::get, delegate);

        observer.observeRequest(request);
        verifyNoInteractions(delegate);

        enabled.set(true);
        observer.observeRequest(request);
        verify(delegate).observeRequest(request);
    }
}
