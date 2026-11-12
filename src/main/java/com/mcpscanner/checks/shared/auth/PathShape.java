package com.mcpscanner.checks.shared.auth;

import java.util.function.UnaryOperator;

enum PathShape {
    MCP_MESSAGE("mcp-message", PathText::messageSibling),
    DOT_SEGMENT("dot-segment", path -> PathText.appendSegment(path, "/./")),
    SEMICOLON("semicolon", path -> PathText.appendSegment(path, "/..;/")),
    TRAILING_SLASH("trailing-slash", PathText::withTrailingSlash),
    TRAILING_DOT("trailing-dot", path -> path + "."),
    CASE("case", PathText::swappedCase),
    DOUBLE_ENCODED_PATH("double-encoded-path", path -> path + "%252f"),
    NULL_BYTE("null-byte", path -> path + "%00"),
    BACKSLASH("backslash", path -> path + "%5c"),
    PERCENT_LETTER("percent-letter", PathText::percentEncodedFirstLetter),
    ENCODED_DOT_SEGMENT("encoded-dot-segment", path -> "/%2e" + path),
    PARENT_TRAVERSAL("parent-traversal", path -> "/x/%2e%2e" + path),
    ENCODED_SLASH("encoded-slash", path -> path + "%2f"),
    ZERO_WIDTH("zero-width", path -> PathText.insertedAfterFirstLetter(path, "%E2%80%8B")),
    WORD_JOINER("word-joiner", path -> PathText.insertedAfterFirstLetter(path, "%E2%81%A0")),
    SOFT_HYPHEN("soft-hyphen", path -> PathText.insertedAfterFirstLetter(path, "%C2%AD")),
    UNICODE_SPACE("unicode-space", path -> path + "%E3%80%80"),
    ENCODED_SPACE("encoded-space", path -> path + "%20"),
    ENCODED_TAB("encoded-tab", path -> path + "%09"),
    ENCODED_CRLF("encoded-crlf", path -> path + "%0d%0a"),
    ACCENT_FOLD("accent-fold", PathText::accentFolded);

    private final String label;
    private final UnaryOperator<String> shape;

    PathShape(String label, UnaryOperator<String> shape) {
        this.label = label;
        this.shape = shape;
    }

    String label() {
        return label;
    }

    String applyTo(String path) {
        return shape.apply(path);
    }
}
