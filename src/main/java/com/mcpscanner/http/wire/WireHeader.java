package com.mcpscanner.http.wire;

import java.util.Locale;

public record WireHeader(String name, String value) {

    public boolean named(String candidate) {
        return name.toLowerCase(Locale.ROOT).equals(candidate.toLowerCase(Locale.ROOT));
    }
}
