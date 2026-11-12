package com.mcpscanner.checks.catalog.filetraversal;

import java.util.Optional;
import java.util.regex.Pattern;

final class BasePath {

    private static final int MAX_LENGTH = 512;
    private static final Pattern WEB_URL = Pattern.compile("(?i)^(?:https?|wss?|ftp)://");
    private static final Pattern DRIVE = Pattern.compile("^[A-Za-z]:[\\\\/]");
    private static final String BACKSLASH = "\\";
    private static final String SLASH = "/";

    private final String value;

    private BasePath(String value) {
        this.value = value;
    }

    static BasePath of(String baseValue) {
        return new BasePath(usable(baseValue) ? baseValue : "");
    }

    String usableValue() {
        return value;
    }

    Optional<String> prefixableValue() {
        return Optional.of(value).filter(usable -> !usable.isEmpty() && !usable.endsWith(SLASH)
                && !usable.endsWith(BACKSLASH));
    }

    Optional<String> directory() {
        int lastSeparator = Math.max(value.lastIndexOf('/'), value.lastIndexOf('\\'));
        return lastSeparator < 0 ? Optional.empty() : Optional.of(value.substring(0, lastSeparator + 1));
    }

    String separator() {
        return directory().filter(directory -> directory.endsWith(BACKSLASH)).map(directory -> BACKSLASH)
                .orElse(SLASH);
    }

    boolean windowsStyle() {
        return separator().equals(BACKSLASH) || DRIVE.matcher(value).find();
    }

    Optional<String> directoryName() {
        return directory().map(directory -> directory.substring(0, directory.length() - 1))
                .map(BasePath::lastSegment)
                .filter(BasePath::namesADirectory);
    }

    private static String lastSegment(String path) {
        return path.substring(Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\')) + 1);
    }

    private static boolean namesADirectory(String name) {
        return !name.isBlank() && !name.equals(".") && !name.equals("..") && !name.endsWith(":");
    }

    private static boolean usable(String baseValue) {
        return baseValue != null && baseValue.length() <= MAX_LENGTH && !WEB_URL.matcher(baseValue).find()
                && baseValue.chars().noneMatch(Character::isISOControl);
    }
}
