package com.mcpscanner.scan.request;

import com.mcpscanner.mcp.McpMethods;
import com.mcpscanner.scan.ScanInventory;

import java.util.List;

public final class RequestFamilyCatalog {

    private RequestFamilyCatalog() {}

    public static List<RequestFamilyBuilder> standard() {
        return List.of(
                new ToolCallRequests(),
                new ResourceReadRequests(),
                new ResourceTemplateReadRequests(),
                new PromptGetRequests(),
                new ListRequests(RequestFamily.TOOLS_LIST, McpMethods.TOOLS_LIST, "tools", ScanInventory::tools),
                new ListRequests(RequestFamily.RESOURCES_LIST, McpMethods.RESOURCES_LIST, "resources",
                        ScanInventory::resources),
                new ListRequests(RequestFamily.RESOURCE_TEMPLATES_LIST, McpMethods.RESOURCE_TEMPLATES_LIST, "resources",
                        ScanInventory::resourceTemplates),
                new ListRequests(RequestFamily.PROMPTS_LIST, McpMethods.PROMPTS_LIST, "prompts",
                        ScanInventory::prompts),
                FixedMessageRequests.universal(RequestFamily.INITIALIZE, ProtocolMessages::initialize),
                FixedMessageRequests.universal(RequestFamily.NOTIFICATION_INITIALIZED,
                        ProtocolMessages::initializedNotification),
                FixedMessageRequests.universal(RequestFamily.NOTIFICATION_CANCELLED,
                        ProtocolMessages::cancelledNotification),
                FixedMessageRequests.universal(RequestFamily.NOTIFICATION_PROGRESS,
                        ProtocolMessages::progressNotification),
                FixedMessageRequests.universal(RequestFamily.NOTIFICATION_ROOTS_LIST_CHANGED,
                        ProtocolMessages::rootsListChangedNotification),
                new CompletionRequests(),
                new FixedMessageRequests(RequestFamily.LOGGING, capabilities -> capabilities.advertises("logging"),
                        ProtocolMessages::setLevel),
                FixedMessageRequests.universal(RequestFamily.PING, ProtocolMessages::ping),
                new SubscriptionRequests(),
                FixedMessageRequests.universal(RequestFamily.SERVER_DISCOVER, ProtocolMessages::serverDiscover),
                new ClientRegistrationRequests());
    }
}
