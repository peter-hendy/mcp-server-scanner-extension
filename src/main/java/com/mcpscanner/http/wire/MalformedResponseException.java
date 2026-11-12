package com.mcpscanner.http.wire;

import java.io.UncheckedIOException;
import java.io.IOException;

final class MalformedResponseException extends UncheckedIOException {

    MalformedResponseException(String message) {
        super(new IOException(message));
    }
}
