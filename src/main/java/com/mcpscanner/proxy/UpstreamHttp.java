package com.mcpscanner.proxy;

import com.mcpscanner.client.ProtocolHttpRequest;
import com.mcpscanner.client.ProtocolHttpResponse;
import com.mcpscanner.client.ProtocolHttpTransport;
import com.mcpscanner.mcp.ServerPingAnswerer;
import com.mcpscanner.mcp.ServerPings;
import com.mcpscanner.mcp.StreamingResponseReader;
import com.mcpscanner.mcp.UnstreamedReply;
import com.mcpscanner.core.protocol.McpRequests;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;

record UpstreamHttp(ProtocolHttpTransport transport, StreamingResponseReader responseReader,
                    Duration responseTimeout, ServerPings serverPings) {

    ProtocolHttpResponse send(ProtocolHttpRequest request) throws IOException, InterruptedException {
        return transport.send(request, responseTimeout);
    }

    ProxyResponse readReply(ProtocolHttpRequest sent, ProtocolHttpResponse response, String requestBody)
            throws IOException {
        UnstreamedReply reply = responseReader.read(response.status(), contentTypeOf(response), response.body(),
                requestBody, pingAnswerer(sent));
        return new ProxyResponse(reply.statusCode(), reply.contentType(), reply.body())
                .withHeaders(RelayedHeaders.ofMultiValued(response.headers()));
    }

    private static String contentTypeOf(ProtocolHttpResponse response) {
        return response.firstHeader("Content-Type").orElse("application/json");
    }

    private ServerPingAnswerer pingAnswerer(ProtocolHttpRequest sent) {
        return serverPings.answerer(sent.headers(), reply -> postDiscardingResponse(sent.uri(), reply));
    }

    private void postDiscardingResponse(URI target, McpRequests.Request reply) {
        try (ProtocolHttpResponse ignored = send(new ProtocolHttpRequest(target, "POST", reply.headers(),
                reply.body()))) {
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException ignored) {
        }
    }
}
