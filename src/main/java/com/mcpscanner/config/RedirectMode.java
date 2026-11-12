package com.mcpscanner.config;

public enum RedirectMode {
    METHOD_PRESERVING_SAME_ORIGIN("Same origin, 307/308 only",
            "Same origin, 307/308 only: follows a 307 or 308 redirect to the same scheme, host and port, "
                    + "which keeps the method and body; other redirects are not followed."),
    SAME_ORIGIN("Same origin, any status", "Same origin, any status: follows any redirect to the same scheme, "
            + "host and port."),
    ALWAYS("Always (risky)",
            "Always: follows redirects to any origin, which may send credentials to a different origin.");

    private final String label;
    private final String description;

    RedirectMode(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String description() {
        return description;
    }

    @Override
    public String toString() {
        return label;
    }
}
