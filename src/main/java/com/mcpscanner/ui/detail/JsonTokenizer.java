package com.mcpscanner.ui.detail;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;

final class JsonTokenizer {

    private static final String PUNCTUATION = "{}[],:";
    private static final String LITERAL_SYMBOLS = "-+.";
    private static final char QUOTE = '"';
    private static final char ESCAPE = '\\';

    List<JsonToken> tokenize(String text) {
        List<JsonToken> tokens = new ArrayList<>();
        int position = 0;
        while (position < text.length()) {
            JsonToken token = next(text, position);
            tokens.add(token);
            position = token.end();
        }
        return tokens;
    }

    private JsonToken next(String text, int start) {
        char first = text.charAt(start);
        if (first == QUOTE) {
            return string(text, start);
        }
        if (PUNCTUATION.indexOf(first) >= 0) {
            return new JsonToken(start, start + 1, JsonTokenKind.PUNCTUATION);
        }
        if (Character.isWhitespace(first)) {
            return run(text, start, Character::isWhitespace, JsonTokenKind.PLAIN);
        }
        if (isLiteralCharacter(first)) {
            return run(text, start, JsonTokenizer::isLiteralCharacter, JsonTokenKind.LITERAL);
        }
        return new JsonToken(start, start + 1, JsonTokenKind.PLAIN);
    }

    private JsonToken string(String text, int start) {
        int end = afterClosingQuote(text, start);
        JsonTokenKind kind = followedByColon(text, end) ? JsonTokenKind.KEY : JsonTokenKind.STRING;
        return new JsonToken(start, end, kind);
    }

    private static int afterClosingQuote(String text, int openingQuote) {
        int position = openingQuote + 1;
        while (position < text.length()) {
            char current = text.charAt(position);
            if (current == QUOTE) {
                return position + 1;
            }
            position += current == ESCAPE ? 2 : 1;
        }
        return text.length();
    }

    private static boolean followedByColon(String text, int position) {
        int next = position;
        while (next < text.length() && Character.isWhitespace(text.charAt(next))) {
            next++;
        }
        return next < text.length() && text.charAt(next) == ':';
    }

    private static JsonToken run(String text, int start, IntPredicate member, JsonTokenKind kind) {
        int end = start;
        while (end < text.length() && member.test(text.charAt(end))) {
            end++;
        }
        return new JsonToken(start, end, kind);
    }

    private static boolean isLiteralCharacter(int character) {
        return Character.isLetterOrDigit(character) || LITERAL_SYMBOLS.indexOf(character) >= 0;
    }
}
