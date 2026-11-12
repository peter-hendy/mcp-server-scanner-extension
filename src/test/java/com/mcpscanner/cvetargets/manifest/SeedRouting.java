package com.mcpscanner.cvetargets.manifest;

import java.util.Map;

@FunctionalInterface
public interface SeedRouting {

    SeedRouting AS_PRIMARY = sessionHeaders -> sessionHeaders;

    Map<String, String> headers(Map<String, String> sessionHeaders);
}
