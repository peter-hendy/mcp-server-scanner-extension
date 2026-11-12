package com.mcpscanner.checks.shared.auth;

import java.util.Locale;

final class PathText {

    private PathText() {}

    static String appendSegment(String path, String suffix) {
        return path.endsWith("/") ? path + suffix.substring(1) : path + suffix;
    }

    static String messageSibling(String path) {
        if (path.equals("/")) {
            return "/mcp_message";
        }
        return (path.endsWith("/") ? path.substring(0, path.length() - 1) : path) + "_message";
    }

    static String withTrailingSlash(String path) {
        return path.endsWith("/") ? path : path + "/";
    }

    static String withTrailingSlashAddedOrRemoved(String path) {
        if (path.equals("/")) {
            return path;
        }
        return path.endsWith("/") ? path.substring(0, path.length() - 1) : path + "/";
    }

    static String directoryOf(String path) {
        String withoutTrailingSlash = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        return withoutTrailingSlash.substring(0, withoutTrailingSlash.lastIndexOf('/') + 1);
    }

    static String swappedCase(String path) {
        StringBuilder result = new StringBuilder(path.length());
        for (char character : path.toCharArray()) {
            result.append(swapAsciiCase(character));
        }
        return result.toString();
    }

    static String percentEncodedFirstLetter(String path) {
        int position = firstAsciiLetter(path);
        return position < 0 ? path : replace(path, position, percent(path.charAt(position)));
    }

    static String insertedAfterFirstLetter(String path, String encoding) {
        int position = firstAsciiLetter(path);
        return insert(path, position < 0 ? path.length() : position + 2, encoding);
    }

    static String accentFolded(String path) {
        for (int position = 1; position < path.length(); position++) {
            String encoded = accentEncoding(path.charAt(position));
            if (encoded != null) {
                return replace(path, position, encoded);
            }
        }
        return path;
    }

    private static String accentEncoding(char character) {
        return switch (Character.toLowerCase(character)) {
            case 'c' -> "%C3%A7";
            case 'e' -> "%C3%A9";
            case 'o' -> "%C3%B6";
            case 'a' -> "%C3%A1";
            default -> null;
        };
    }

    private static char swapAsciiCase(char character) {
        if (character >= 'a' && character <= 'z') {
            return Character.toUpperCase(character);
        }
        if (character >= 'A' && character <= 'Z') {
            return Character.toLowerCase(character);
        }
        return character;
    }

    private static int firstAsciiLetter(String path) {
        for (int position = 1; position < path.length(); position++) {
            if (Character.isLetter(path.charAt(position))) {
                return position;
            }
        }
        return -1;
    }

    private static String replace(String path, int position, String value) {
        return path.substring(0, position) + value + path.substring(position + 1);
    }

    private static String insert(String path, int position, String value) {
        return path.substring(0, Math.min(position, path.length())) + value
                + path.substring(Math.min(position, path.length()));
    }

    private static String percent(char character) {
        return String.format(Locale.ROOT, "%%%02X", (int) character);
    }
}
