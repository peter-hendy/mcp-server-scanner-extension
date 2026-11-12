package com.mcpscanner.scan;

public record FixedConnectionFacts(String transport, String connectAs) implements ScanConnectionFacts {

    public static final FixedConnectionFacts NONE = new FixedConnectionFacts("", "");
}
