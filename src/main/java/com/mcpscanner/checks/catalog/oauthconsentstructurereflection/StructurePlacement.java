package com.mcpscanner.checks.catalog.oauthconsentstructurereflection;

enum StructurePlacement {
    ELEMENT("an HTML element"),
    ATTRIBUTE("a new HTML attribute on an existing tag");

    private final String parsedAs;

    StructurePlacement(String parsedAs) {
        this.parsedAs = parsedAs;
    }

    String parsedAs() {
        return parsedAs;
    }
}
