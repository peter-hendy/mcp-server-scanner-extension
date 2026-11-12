package com.mcpscanner;

import burp.api.montoya.http.Http;
import com.mcpscanner.client.BurpProtocolHttpTransport;
import com.mcpscanner.client.JdkProtocolHttpTransport;
import com.mcpscanner.client.McpHttpClients;
import com.mcpscanner.client.ProtocolHttpTransport;
import com.mcpscanner.client.SelectableProtocolTransport;
import com.mcpscanner.client.StreamedReplyFallbackTransport;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.proxy.SentinelStrippingTransport;
import com.mcpscanner.proxy.UpstreamCaptures;

import java.util.function.BooleanSupplier;

final class SharedUpstreamTransport {

    private final BurpProtocolHttpTransport burpTransport;
    private final StreamedReplyFallbackTransport burpUnlessStreamed;
    private final ProtocolHttpTransport sentinelFreeTransport;

    private SharedUpstreamTransport(BurpProtocolHttpTransport burpTransport, BooleanSupplier useBurpNetworking,
                                    McpEventLog eventLog, UpstreamCaptures captures) {
        ProtocolHttpTransport jdkTransport = new JdkProtocolHttpTransport(McpHttpClients.upstream());
        this.burpTransport = burpTransport;
        this.burpUnlessStreamed = new StreamedReplyFallbackTransport(burpTransport, jdkTransport,
                captures::forwarding, eventLog);
        this.sentinelFreeTransport = new SentinelStrippingTransport(captures.recording(
                new SelectableProtocolTransport(useBurpNetworking, burpUnlessStreamed, jdkTransport, eventLog)));
    }

    static SharedUpstreamTransport over(Http http, BooleanSupplier useBurpNetworking, McpEventLog eventLog,
                                        UpstreamCaptures captures) {
        return new SharedUpstreamTransport(new BurpProtocolHttpTransport(http), useBurpNetworking, eventLog,
                captures);
    }

    ProtocolHttpTransport transport() {
        return sentinelFreeTransport;
    }

    void forgetStreamingRoutes() {
        burpUnlessStreamed.forgetStreamingRoutes();
    }

    void close() {
        burpTransport.close();
    }
}
