package com.mcpscanner.scan.request;

import com.mcpscanner.core.protocol.McpRequests;

import static com.mcpscanner.scan.request.RequestFamilyGroup.AUTHORIZATION;
import static com.mcpscanner.scan.request.RequestFamilyGroup.CAPABILITIES;
import static com.mcpscanner.scan.request.RequestFamilyGroup.INVOCATION;
import static com.mcpscanner.scan.request.RequestFamilyGroup.LIFECYCLE;
import static com.mcpscanner.scan.request.RequestFamilyGroup.LISTING;
import static com.mcpscanner.scan.request.RequestFamilyGroup.NOTIFICATIONS;

public enum RequestFamily {
    TOOLS_CALL("tools_call", "Tool calls", INVOCATION),
    RESOURCES_READ("resources_read", "Resource reads", INVOCATION),
    RESOURCE_TEMPLATES_READ("resource_templates_read", "Resource template reads", INVOCATION,
            "Each template is expanded into a concrete URI."),
    PROMPTS_GET("prompts_get", "Prompt gets", INVOCATION),
    TOOLS_LIST("tools_list", "Tools", LISTING),
    RESOURCES_LIST("resources_list", "Resources", LISTING),
    RESOURCE_TEMPLATES_LIST("resource_templates_list", "Resource templates", LISTING),
    PROMPTS_LIST("prompts_list", "Prompts", LISTING),
    INITIALIZE("initialize", "Initialize", LIFECYCLE, "", true),
    NOTIFICATION_INITIALIZED("notification_initialized", "Initialized", NOTIFICATIONS, "", true),
    NOTIFICATION_CANCELLED("notification_cancelled", "Cancelled", NOTIFICATIONS),
    NOTIFICATION_PROGRESS("notification_progress", "Progress", NOTIFICATIONS),
    NOTIFICATION_ROOTS_LIST_CHANGED("notification_roots_list_changed", "Roots list changed", NOTIFICATIONS),
    COMPLETION("completion", "Completions", CAPABILITIES),
    LOGGING("logging", "Logging level", CAPABILITIES, "", true),
    PING("ping", "Ping", LIFECYCLE),
    SUBSCRIBE("subscribe", "Resource subscribe and unsubscribe", CAPABILITIES),
    SERVER_DISCOVER("server_discover", "Server discovery", LIFECYCLE, "Protocol version 2026-07-28."),
    CLIENT_REGISTRATION("oauth_client_registration", "Dynamic client registration", AUTHORIZATION,
            "Only sent when the server advertises a registration endpoint.");

    private final String id;
    private final String label;
    private final RequestFamilyGroup group;
    private final String hint;
    private final boolean handshakeOnly;

    RequestFamily(String id, String label, RequestFamilyGroup group) {
        this(id, label, group, "");
    }

    RequestFamily(String id, String label, RequestFamilyGroup group, String hint) {
        this(id, label, group, hint, false);
    }

    RequestFamily(String id, String label, RequestFamilyGroup group, String hint, boolean handshakeOnly) {
        this.id = id;
        this.label = label;
        this.group = group;
        this.hint = hint;
        this.handshakeOnly = handshakeOnly;
    }

    public boolean definedIn(String protocolVersion) {
        return !handshakeOnly || !McpRequests.MODERN_VERSION.equals(protocolVersion);
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public RequestFamilyGroup group() {
        return group;
    }

    public String hint() {
        return hint;
    }
}
