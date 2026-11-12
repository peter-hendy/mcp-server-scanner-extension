package com.mcpscanner.checks.shared.auth.oracle;

import com.fasterxml.jackson.databind.JsonNode;
import com.mcpscanner.checks.shared.jsonrpc.JsonNodes;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

final class CredentialRequirementText {

    private static final int MAX_DEPTH = 8;
    private static final String NOT_AN_ARGUMENT =
            "(?![\\w./-]|\\s+(?:argument|parameter|field|property|file|path)\\b)";
    private static final String SPECIFIC_CREDENTIAL = "(?:credentials?|api[ _-]?key|apikey|authori[sz]ation header)";
    private static final String QUALIFIED_TOKEN =
            "(?:bearer|access|auth|authentication|api|session|oauth|id)[ _-]?(?:token|key)";
    private static final String ANY_CREDENTIAL = "(?:token|" + SPECIFIC_CREDENTIAL + "|access[ _-]?key)";
    private static final String INVALIDITY = "(?:missing|invalid|expired|revoked|malformed|incorrect|unrecognized|no)";
    private static final List<Pattern> PHRASES = List.of(
            phrase("\\b(?:requires?|needs?)\\s+(?:an?\\s+|the\\s+|your\\s+)?valid\\s+(?:[\\w./-]+\\s+){0,3}?"
                    + ANY_CREDENTIAL + NOT_AN_ARGUMENT),
            phrase("\\b(?:requires?|needs?)\\s+(?:authentication|login|sign[ -]?in)\\b"),
            phrase("\\b" + INVALIDITY + "\\s+(?:or\\s+\\w+\\s+)?" + QUALIFIED_TOKEN + NOT_AN_ARGUMENT),
            phrase("\\b" + INVALIDITY + "\\s+(?:or\\s+\\w+\\s+)?" + SPECIFIC_CREDENTIAL + NOT_AN_ARGUMENT),
            phrase("\\b(?:missing|expired|revoked|(?:invalid|missing|expired) or (?:invalid|missing|expired))"
                    + "\\s+token" + NOT_AN_ARGUMENT),
            phrase("\\b(?:authentication|authori[sz]ation|login|sign[ -]?in)\\s+(?:is\\s+)?required\\b"),
            phrase("\\b(?:not authenticated|unauthenticated)\\b"),
            phrase("(?:^|:)\\s*unauthori[sz]ed\\s*[.!]?\\s*$"));

    private CredentialRequirementText() {}

    static boolean statedIn(JsonNode node) {
        return statedIn(node, 0);
    }

    private static boolean statedIn(JsonNode node, int depth) {
        if (node == null || depth > MAX_DEPTH) {
            return false;
        }
        if (node.isTextual()) {
            return states(node.asText());
        }
        if (node.isObject()) {
            return JsonNodes.fields(node).stream().map(Map.Entry::getValue)
                    .anyMatch(value -> statedIn(value, depth + 1));
        }
        return node.isArray() && JsonNodes.elements(node).stream().anyMatch(element -> statedIn(element, depth + 1));
    }

    private static boolean states(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return PHRASES.stream().anyMatch(phrase -> phrase.matcher(lower).find());
    }

    private static Pattern phrase(String regex) {
        return Pattern.compile(regex, Pattern.MULTILINE);
    }
}
