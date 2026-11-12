package com.mcpscanner.checks.catalog.oauthconsentstructurereflection;

import com.mcpscanner.checks.shared.html.HtmlDocument;

import java.util.Optional;

record StructureMarker(String token, String elementName, String attributeName) {

    private static final String PREFIX = "mcpscan-";

    static StructureMarker withNonce(String nonce) {
        return new StructureMarker(PREFIX + nonce, PREFIX + "el-" + nonce, PREFIX + "at-" + nonce);
    }

    String payload() {
        return token + "\"' " + attributeName + "=\"1\"></script><" + elementName + "></" + elementName + ">";
    }

    Optional<StructurePlacement> placementIn(HtmlDocument document) {
        if (document.containsActiveElement(elementName)) {
            return Optional.of(StructurePlacement.ELEMENT);
        }
        if (document.containsActiveAttribute(attributeName)) {
            return Optional.of(StructurePlacement.ATTRIBUTE);
        }
        return Optional.empty();
    }
}
