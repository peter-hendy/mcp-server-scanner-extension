package com.mcpscanner.scan.request;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mcpscanner.ExtensionMetadata;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.mcpscanner.mcp.McpMethods;
import com.mcpscanner.mcp.McpObjectMapper;
import com.mcpscanner.core.protocol.McpRequests;

final class ProtocolMessages {

    static final String CLIENT_WEBSITE = "https://portswigger.net/burp";
    static final String CLIENT_ICON = "https://portswigger.net/favicon.ico";
    static final String CANCELLED_REQUEST_ID = "mcp-scanner-request";
    static final String LOG_LEVEL = "debug";

    private ProtocolMessages() {}

    private static ObjectNode initializeParams() {
        try {
            return (ObjectNode) McpObjectMapper.INSTANCE.readTree(McpRequests.initializeParams(
                    McpRequests.supportedInitializeVersions().get(0), ExtensionMetadata.SCANNER_CLIENT_NAME,
                    ExtensionMetadata.VERSION));
        } catch (JsonProcessingException unreachable) {
            throw new IllegalStateException(unreachable);
        }
    }

    static McpMessage initialize() {
        ObjectNode params = initializeParams();
        params.set("capabilities", clientCapabilities());
        params.set("clientInfo", clientInfo());
        return McpMessage.request(RequestFamily.INITIALIZE, McpMethods.INITIALIZE, McpParams.withMeta(params));
    }

    static McpMessage initializedNotification() {
        return McpMessage.notification(RequestFamily.NOTIFICATION_INITIALIZED,
                McpMethods.NOTIFICATIONS_INITIALIZED, McpParams.empty());
    }

    static McpMessage cancelledNotification() {
        ObjectNode params = McpParams.empty()
                .put("requestId", CANCELLED_REQUEST_ID)
                .put("reason", "Cancelled by " + ExtensionMetadata.NAME);
        return McpMessage.notification(RequestFamily.NOTIFICATION_CANCELLED, McpMethods.NOTIFICATIONS_CANCELLED,
                params);
    }

    static McpMessage progressNotification() {
        ObjectNode params = McpParams.empty()
                .put("progressToken", McpParams.PROGRESS_TOKEN)
                .put("progress", 1)
                .put("total", 2)
                .put("message", "Progress from " + ExtensionMetadata.NAME);
        return McpMessage.notification(RequestFamily.NOTIFICATION_PROGRESS, McpMethods.NOTIFICATIONS_PROGRESS, params);
    }

    static McpMessage rootsListChangedNotification() {
        return McpMessage.notification(RequestFamily.NOTIFICATION_ROOTS_LIST_CHANGED,
                "notifications/roots/list_changed", McpParams.empty());
    }

    static McpMessage setLevel() {
        return McpMessage.request(RequestFamily.LOGGING, McpMethods.LOGGING_SET_LEVEL,
                McpParams.withMeta(McpParams.empty().put("level", LOG_LEVEL)));
    }

    static McpMessage ping() {
        return McpMessage.request(RequestFamily.PING, McpMethods.PING, McpParams.metaOnly());
    }

    static McpMessage serverDiscover() {
        return McpMessage.parameterlessRequest(RequestFamily.SERVER_DISCOVER, McpMethods.SERVER_DISCOVER);
    }

    private static ObjectNode clientCapabilities() {
        ObjectNode capabilities = McpParams.empty();
        capabilities.putObject("roots").put("listChanged", true);
        capabilities.putObject("sampling");
        capabilities.putObject("elicitation");
        return capabilities;
    }

    private static ObjectNode clientInfo() {
        ObjectNode clientInfo = McpParams.empty()
                .put("name", ExtensionMetadata.NAME)
                .put("version", ExtensionMetadata.VERSION)
                .put("title", ExtensionMetadata.NAME)
                .put("description", "Burp Suite MCP server scanner")
                .put("websiteUrl", CLIENT_WEBSITE);
        ObjectNode icon = clientInfo.putArray("icons").addObject()
                .put("src", CLIENT_ICON)
                .put("mimeType", "image/x-icon");
        icon.putArray("sizes").add("32x32");
        return clientInfo;
    }
}
