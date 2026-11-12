package com.mcpscanner.mcp;

public final class ExtensionVersion {

    public static final String CURRENT = resolve();

    private static final String FALLBACK = "2.0.0";

    private ExtensionVersion() {
    }

    private static String resolve() {
        String packaged = ExtensionVersion.class.getPackage().getImplementationVersion();
        return (packaged != null && !packaged.isBlank()) ? packaged : FALLBACK;
    }
}
