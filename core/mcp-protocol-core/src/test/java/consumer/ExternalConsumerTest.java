package consumer;

import com.mcpscanner.core.protocol.McpFlow;
import com.mcpscanner.core.protocol.McpRequests;
import com.mcpscanner.core.protocol.McpResponses;

public final class ExternalConsumerTest {
    public static void main(String[] args) {
        McpRequests.Request request = McpRequests.modern("tools/list", "consumer", "1", null, 17);
        if (!request.body().contains("\"id\":17")) throw new AssertionError("request ID missing");
        McpFlow.Session session = new McpFlow.Session(McpRequests.MODERN_VERSION, null, null, "consumer", "1");
        if (!session.request("tools/list", null, 18).body().contains("\"id\":18"))
            throw new AssertionError("session request ID missing");
        McpResponses.Reply reply = McpResponses.parse("{\"jsonrpc\":\"2.0\",\"id\":18,\"result\":{}}", 1024);
        if (reply.id() != 18) throw new AssertionError("reply ID mismatch");
        McpResponses.Reply discovery = McpResponses.parse("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"capabilities\":{},\"supportedVersions\":[\"2026-07-28\"]}}", 1024);
        if (!McpResponses.modernDiscovery(discovery)) throw new AssertionError("discovery rejected");
        McpResponses.Reply offer = McpResponses.parse("{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"code\":-32602,\"data\":{\"supported\":[\"2025-03-26\"]}}}", 1024);
        if (!"2025-03-26".equals(McpResponses.counterOffer(offer))) throw new AssertionError("counteroffer rejected");
        System.out.println("ExternalConsumerTest passed");
    }
}
