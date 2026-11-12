package com.mcpscanner.scan.request;

@FunctionalInterface
public interface RequestFamilyToggles {

    boolean isEnabled(RequestFamily family);
}
