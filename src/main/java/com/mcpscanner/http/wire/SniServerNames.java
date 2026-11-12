package com.mcpscanner.http.wire;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIServerName;
import java.util.List;
import java.util.regex.Pattern;

final class SniServerNames {

    private static final Pattern IPV4_LITERAL = Pattern.compile("[0-9.]+");

    private SniServerNames() {
    }

    static List<SNIServerName> forHost(String host) {
        if (isIpLiteral(host)) {
            return List.of();
        }
        try {
            return List.of(new SNIHostName(host));
        } catch (IllegalArgumentException notAnSniName) {
            return List.of();
        }
    }

    private static boolean isIpLiteral(String host) {
        return host.contains(":") || IPV4_LITERAL.matcher(host).matches();
    }
}
