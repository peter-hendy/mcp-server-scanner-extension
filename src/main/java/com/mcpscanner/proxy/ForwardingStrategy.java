package com.mcpscanner.proxy;

import java.io.IOException;
import java.util.Map;

interface ForwardingStrategy {

    String label();

    ProxyResponse forward(String body, Map<String, String> headers, AuditRequest audit)
            throws IOException, InterruptedException;
}
