package com.mcpscanner.checks.shared.auth;

final class HeaderValues {

    private HeaderValues() {}

    static boolean safeAscii(String value) {
        return value != null && value.chars().allMatch(character ->
                character >= '!' && character <= '~' && character != '"' && character != '\\'
                        && character != '#');
    }

    static boolean simpleAuthority(String authority) {
        return authority != null && authority.matches("[A-Za-z0-9.-]+(?::[0-9]+)?");
    }
}
