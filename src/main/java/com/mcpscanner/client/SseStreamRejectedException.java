package com.mcpscanner.client;

import java.io.IOException;

final class SseStreamRejectedException extends IOException {

    SseStreamRejectedException(String message) {
        super(message);
    }
}
