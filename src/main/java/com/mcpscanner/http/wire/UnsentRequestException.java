package com.mcpscanner.http.wire;

import java.io.IOException;

public final class UnsentRequestException extends IOException {

    UnsentRequestException(IOException cause) {
        super(cause.getMessage(), cause);
    }
}
