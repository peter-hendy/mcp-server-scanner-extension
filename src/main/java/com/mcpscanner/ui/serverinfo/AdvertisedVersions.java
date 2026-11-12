package com.mcpscanner.ui.serverinfo;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public final class AdvertisedVersions {

    private static final String FIELD = "supportedVersions";
    private static final String NOT_SENT = "not sent";
    private static final String SEPARATOR = ", ";

    private AdvertisedVersions() {
    }

    public static String of(JsonNode result) {
        JsonNode versions = result == null ? null : result.get(FIELD);
        if (versions == null || !versions.isArray() || versions.isEmpty()) {
            return NOT_SENT;
        }
        return StreamSupport.stream(versions.spliterator(), false)
                .map(JsonNode::asText)
                .collect(Collectors.joining(SEPARATOR));
    }
}
