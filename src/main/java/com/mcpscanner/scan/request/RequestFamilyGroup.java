package com.mcpscanner.scan.request;

public enum RequestFamilyGroup {
    INVOCATION("Invocation", ""),
    LISTING("Listing", "Each list request carries a pagination cursor."),
    LIFECYCLE("Lifecycle", ""),
    NOTIFICATIONS("Notifications", ""),
    CAPABILITIES("Other capabilities", ""),
    AUTHORIZATION("Authorization", "");

    private final String title;
    private final String description;

    RequestFamilyGroup(String title, String description) {
        this.title = title;
        this.description = description;
    }

    public String title() {
        return title;
    }

    public String description() {
        return description;
    }

    public boolean invokesInventoryItems() {
        return this == INVOCATION;
    }
}
