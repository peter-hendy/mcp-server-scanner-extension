package com.mcpscanner.proxy;

import com.mcpscanner.client.TransportType;

record ProxyConnection(long generation, long sessionGeneration, String endpoint,
                       TransportType transport, String sseUrl) {}
