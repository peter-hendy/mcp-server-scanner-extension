package com.mcpscanner.ui.detail;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static com.mcpscanner.ui.detail.JsonTokenKind.KEY;
import static com.mcpscanner.ui.detail.JsonTokenKind.LITERAL;
import static com.mcpscanner.ui.detail.JsonTokenKind.PLAIN;
import static com.mcpscanner.ui.detail.JsonTokenKind.PUNCTUATION;
import static com.mcpscanner.ui.detail.JsonTokenKind.STRING;
import static org.assertj.core.api.Assertions.assertThat;

class JsonTokenizerTest {

    private static final String TRICKY = "{\"a\\\"}\" : \"x\\\\\", \"n\" : -1.5e3, \"t\" : [ true, null ], \"s\" : \"{[,:]}\" }";

    @Test
    void tokensCoverTheWholeTextInOrder() {
        List<JsonToken> tokens = new JsonTokenizer().tokenize(TRICKY);

        assertThat(tokens.stream().map(token -> token.text(TRICKY)).collect(Collectors.joining())).isEqualTo(TRICKY);
    }

    @Test
    void escapedQuotesAndBracesInsideStringsDoNotEndTheString() {
        List<String> described = describe(TRICKY);

        assertThat(described).containsExactly(
                "PUNCTUATION {", "KEY \"a\\\"}\"", "PUNCTUATION :", "STRING \"x\\\\\"", "PUNCTUATION ,",
                "KEY \"n\"", "PUNCTUATION :", "LITERAL -1.5e3", "PUNCTUATION ,",
                "KEY \"t\"", "PUNCTUATION :", "PUNCTUATION [", "LITERAL true", "PUNCTUATION ,", "LITERAL null",
                "PUNCTUATION ]", "PUNCTUATION ,", "KEY \"s\"", "PUNCTUATION :", "STRING \"{[,:]}\"",
                "PUNCTUATION }");
    }

    @Test
    void anUnterminatedStringRunsToTheEnd() {
        assertThat(describe("[\"open")).containsExactly("PUNCTUATION [", "STRING \"open");
    }

    @Test
    void whitespaceIsPlain() {
        assertThat(new JsonTokenizer().tokenize("  \n").get(0).kind()).isEqualTo(PLAIN);
    }

    @Test
    void kindsAreDistinct() {
        assertThat(List.of(KEY, STRING, LITERAL, PUNCTUATION, PLAIN)).doesNotHaveDuplicates();
    }

    private static List<String> describe(String text) {
        return new JsonTokenizer().tokenize(text).stream()
                .filter(token -> token.kind() != PLAIN)
                .map(token -> token.kind() + " " + token.text(text))
                .collect(Collectors.toList());
    }
}
