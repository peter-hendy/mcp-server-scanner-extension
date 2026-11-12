package com.mcpscanner.client;

import java.io.IOException;
import java.time.Duration;

public interface ProtocolHttpTransport {

    ProtocolHttpResponse send(ProtocolHttpRequest request, Duration timeout) throws IOException, InterruptedException;
}
