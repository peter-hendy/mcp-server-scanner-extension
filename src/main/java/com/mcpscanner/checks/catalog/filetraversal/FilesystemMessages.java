package com.mcpscanner.checks.catalog.filetraversal;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class FilesystemMessages {

    private static final Pattern DENIAL = Pattern.compile(
            "(?i)denied|forbidden|not permitted|outside (?:of )?(?:the )?(?:allowed|root|workspace|sandbox|read|base)"
                    + "|\\bEACCES\\b|\\bEPERM\\b");
    private static final Pattern NOT_FOUND = Pattern.compile(
            "(?i)\\bENOENT\\b|no such file|does not exist|not found|cannot find");
    private static final Pattern NAMED_ROOT = Pattern.compile("not in\\s+(/[^\\s\"'`,;)]+)");

    private FilesystemMessages() {}

    static boolean isDenial(String text) {
        return DENIAL.matcher(text).find();
    }

    static boolean isNotFound(String text) {
        return NOT_FOUND.matcher(text).find();
    }

    static Optional<String> deniedRoot(String text, String controlMarker) {
        if (!text.contains(controlMarker) || !isDenial(text)) {
            return Optional.empty();
        }
        Matcher matcher = NAMED_ROOT.matcher(text);
        if (!matcher.find()) {
            return Optional.empty();
        }
        String root = matcher.group(1);
        return matcher.find() || !usableRoot(root) || root.contains(controlMarker)
                ? Optional.empty() : Optional.of(root);
    }

    static boolean reportsMissing(String text, String path) {
        return text.contains(path) && !isDenial(text) && isNotFound(text);
    }

    private static boolean usableRoot(String root) {
        return !root.equals("/") && !root.endsWith("/") && !root.contains("..") && !root.contains("//");
    }
}
