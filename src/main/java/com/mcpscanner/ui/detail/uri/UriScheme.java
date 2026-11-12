package com.mcpscanner.ui.detail.uri;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class UriScheme {

    static final Pattern LEADING = Pattern.compile("^([A-Za-z][A-Za-z0-9+.\\-]*):(//)?");

    private UriScheme() {
    }

    public static String of(String uri) {
        if (uri == null) {
            return "";
        }
        Matcher scheme = LEADING.matcher(uri);
        return scheme.find() ? scheme.group(1) : "";
    }
}
