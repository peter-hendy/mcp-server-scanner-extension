package com.mcpscanner.scan.request;

import burp.api.montoya.http.handler.HttpHandler;
import burp.api.montoya.http.handler.HttpRequestToBeSent;
import burp.api.montoya.http.handler.HttpResponseReceived;
import burp.api.montoya.http.handler.RequestToBeSentAction;
import burp.api.montoya.http.handler.ResponseReceivedAction;
import burp.api.montoya.http.message.HttpRequestResponse;
import com.mcpscanner.mcp.ScannerSentinels;

public final class GeneratedRegistrationCleanupHandler implements HttpHandler {

    private final RegisteredClientCleanup cleanup;
    private final RegistrationCleanupQueue cleanupQueue;
    private final AwaitedMessageIds awaitingAnswer = new AwaitedMessageIds();

    public GeneratedRegistrationCleanupHandler(RegisteredClientCleanup cleanup, RegistrationCleanupQueue cleanupQueue) {
        this.cleanup = cleanup;
        this.cleanupQueue = cleanupQueue;
    }

    @Override
    public RequestToBeSentAction handleHttpRequestToBeSent(HttpRequestToBeSent request) {
        if (!request.hasHeader(ScannerSentinels.GENERATED_REGISTRATION_HEADER)) {
            return RequestToBeSentAction.continueWith(request);
        }
        awaitingAnswer.add(request.messageId());
        return RequestToBeSentAction.continueWith(
                request.withRemovedHeader(ScannerSentinels.GENERATED_REGISTRATION_HEADER));
    }

    @Override
    public ResponseReceivedAction handleHttpResponseReceived(HttpResponseReceived response) {
        if (awaitingAnswer.remove(response.messageId())) {
            HttpRequestResponse registration =
                    HttpRequestResponse.httpRequestResponse(response.initiatingRequest(), response);
            cleanupQueue.enqueue(registration, cleanup);
        }
        return ResponseReceivedAction.continueWith(response);
    }
}
