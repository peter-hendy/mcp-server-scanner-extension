package com.mcpscanner.ui.detail.uri;

import com.mcpscanner.mcp.uritemplate.UriTemplate;
import org.junit.jupiter.api.Test;

import javax.swing.JTextPane;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class UriTemplateViewTest {

    private static final String TEMPLATE = "repo://{owner}/{repo}/issues{?state,labels*}";

    @Test
    void highlightsExactlyTheExpressions() {
        JTextPane pane = (JTextPane) UriTemplateView.highlighted(UriTemplate.parse(TEMPLATE).orElseThrow());

        assertThat(highlightedText(pane)).isEqualTo("{owner}{repo}{?state,labels*}");
    }

    @Test
    void theTemplateIsPlainTextNotMarkup() {
        JTextPane pane = (JTextPane) UriTemplateView.highlighted(UriTemplate.parse("<html><b>{x}").orElseThrow());

        assertThat(pane.getContentType()).isEqualTo("text/plain");
        assertThat(pane.getText()).isEqualTo("<html><b>{x}");
        assertThat(pane.isEditable()).isFalse();
    }

    private static String highlightedText(JTextPane pane) {
        StyledDocument document = pane.getStyledDocument();
        StringBuilder highlighted = new StringBuilder();
        IntStream.range(0, document.getLength())
                .filter(offset -> StyleConstants.isBold(document.getCharacterElement(offset).getAttributes()))
                .forEach(offset -> highlighted.append(pane.getText().charAt(offset)));
        return highlighted.toString();
    }
}
