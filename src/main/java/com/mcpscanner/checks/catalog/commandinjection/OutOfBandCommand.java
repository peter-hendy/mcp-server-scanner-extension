package com.mcpscanner.checks.catalog.commandinjection;

import java.util.regex.Pattern;

final class OutOfBandCommand {

    private static final Pattern HOST_TEXT = Pattern.compile("[A-Za-z0-9.:-]{3,}");
    private static final String EMPTY_QUOTES = "''";

    private OutOfBandCommand() {}

    static String reaching(String collaboratorHost) {
        String host = shellJoined(collaboratorHost);
        return "nslookup " + host + "||getent hosts " + host + "||curl -s http://" + host + "/||wget -q -O- http://"
                + host + "/";
    }

    static String shellJoined(String host) {
        if (host == null || !HOST_TEXT.matcher(host).matches()) {
            throw new IllegalArgumentException("not a Collaborator host: " + host);
        }
        int middle = host.length() / 2;
        return host.substring(0, middle) + EMPTY_QUOTES + host.substring(middle);
    }
}
