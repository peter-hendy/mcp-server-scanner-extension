package com.mcpscanner.core.oauth;

import java.util.Locale;
import java.util.regex.Pattern;

final class LoopbackHost {
    private static final Pattern IPV4_LOOPBACK = Pattern.compile("127(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}");

    private LoopbackHost() {}

    static boolean matches(String host) {
        if (host == null) return false;
        String name = host.toLowerCase(Locale.ROOT);
        return name.equals("localhost") || name.equals("[::1]")
                || IPV4_LOOPBACK.matcher(name).matches();
    }
}
