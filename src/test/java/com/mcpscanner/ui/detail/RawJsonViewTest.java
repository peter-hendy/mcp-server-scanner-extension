package com.mcpscanner.ui.detail;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;

import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.Color;
import java.awt.Font;
import java.awt.Insets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static com.mcpscanner.testutil.Json.parse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class RawJsonViewTest {

    private final List<String> copied = new ArrayList<>();
    private final RawJsonView view = new RawJsonView("The whole entry as the server sent it.", copied::add);

    @Test
    void keyOrderIsKeptAsReceived() {
        view.show(parse("{\"zeta\":1,\"alpha\":{\"m\":2,\"b\":3}}"));

        String text = view.displayedText();
        assertThat(text.indexOf("\"zeta\"")).isLessThan(text.indexOf("\"alpha\""));
        assertThat(text.indexOf("\"m\"")).isLessThan(text.indexOf("\"b\""));
    }

    @Test
    void aToolEntryKeepsItsNonAlphabeticalOrder() {
        view.show(parse("{\"name\":\"t\",\"inputSchema\":{\"type\":\"object\",\"properties\":{}},\"annotations\":{}}"));

        String text = view.displayedText();
        assertThat(text.indexOf("\"name\"")).isLessThan(text.indexOf("\"inputSchema\""));
        assertThat(text.indexOf("\"inputSchema\"")).isLessThan(text.indexOf("\"annotations\""));
        assertThat(text.indexOf("\"type\"")).isLessThan(text.indexOf("\"properties\""));
    }

    @Test
    void theNodeIsPrettyPrinted() {
        view.show(parse("{\"a\":[1,2]}"));

        assertThat(view.displayedText()).isEqualTo("{\n  \"a\" : [ 1, 2 ]\n}");
    }

    @Test
    void keysStringsLiteralsAndPunctuationAreColouredByKind() {
        view.show(parse("{\"k\":\"v\\\"}\",\"n\":1,\"t\":true}"));
        String text = view.displayedText();

        Color firstKey = colourAt(text.indexOf("\"k\""));
        Color secondKey = colourAt(text.indexOf("\"n\""));
        Color string = colourAt(text.indexOf("\"v"));
        Color braceInString = colourAt(text.indexOf("}\""));
        Color number = colourAt(text.indexOf("1"));
        Color bool = colourAt(text.indexOf("true"));
        Color brace = colourAt(0);

        assertThat(firstKey).isEqualTo(secondKey);
        assertThat(braceInString).isEqualTo(string);
        assertThat(number).isEqualTo(bool);
        assertThat(List.of(firstKey, string, number, brace)).doesNotHaveDuplicates();
    }

    @Test
    void theTextIsMonospacedAndReadOnly() {
        assertThat(view.textPane().isEditable()).isFalse();
        assertThat(view.textPane().getFont().getFamily())
                .isEqualTo(new Font(Font.MONOSPACED, Font.PLAIN, 12).getFamily());
    }

    @Test
    void linesWrapByDefaultAndTheToggleTurnsWrappingOff() {
        view.show(parse("{\"long\":\"" + "x".repeat(400) + "\"}"));

        assertThat(view.wrapLines().isSelected()).isTrue();
        assertThat(view.textPane().getScrollableTracksViewportWidth()).isTrue();

        view.wrapLines().doClick();

        assertThat(view.textPane().getScrollableTracksViewportWidth()).isFalse();
    }

    @Test
    void copyPlacesTheWholePrettyTextOnTheClipboard() {
        view.show(parse("{\"a\":{\"b\":[1,2,3]}}"));

        view.copyButton().doClick();

        assertThat(copied).containsExactly(view.displayedText());
    }

    @Test
    void theHintIsShownLiterally() {
        RawJsonView hostile = new RawJsonView("<html><img src=http://x>", copied::add);

        JLabel hint = Components.within(hostile, JLabel.class).get(0);
        assertThat(Components.htmlDisabled(hint)).isTrue();
        assertThat(hint.getText()).isEqualTo("<html><img src=http://x>");
    }

    @Test
    void theTextSitsInAScrollPane() {
        assertThat(Components.within(view, JScrollPane.class)).isNotEmpty();
    }

    @Test
    void showingNothingClearsTheText() {
        view.show(parse("{\"a\":1}"));

        view.show(null);

        assertThat(view.displayedText()).isEmpty();
    }

    @Test
    void aFiveThousandLineDocumentRendersQuickly() {
        ArrayNode records = JsonNodeFactory.instance.arrayNode();
        for (int index = 0; index < 1_500; index++) {
            records.addObject().put("id", index).put("name", "item \"" + index + "\" {x}").put("active", true);
        }

        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> view.show(records));

        assertThat(view.displayedText().lines().count()).isGreaterThan(5_000);
    }

    @Test
    void theToolbarIsInsetLikeTheOtherDetailSections() {
        Insets insets = view.copyButton().getParent().getInsets();

        assertThat(insets.left).isEqualTo(RawJsonView.PADDING);
        assertThat(insets.right).isEqualTo(RawJsonView.PADDING);
        assertThat(insets.top).isEqualTo(RawJsonView.PADDING);
    }

    @Test
    void textBeyondTheHighlightLimitIsShownUncoloured() {
        view.show(parse("{\"big\":\"" + "x".repeat(JsonDocumentBuilder.HIGHLIGHT_LIMIT) + "\"}"));

        assertThat(view.displayedText()).startsWith("{\n  \"big\"");
        assertThat(foregroundIsSetAt(view.displayedText().indexOf("\"big\""))).isFalse();
        assertThat(foregroundIsSetAt(view.displayedText().indexOf("xxx"))).isFalse();
    }

    @Test
    void textWithinTheHighlightLimitIsColoured() {
        view.show(parse("{\"big\":\"" + "x".repeat(JsonDocumentBuilder.HIGHLIGHT_LIMIT - 64) + "\"}"));

        assertThat(foregroundIsSetAt(view.displayedText().indexOf("\"big\""))).isTrue();
    }

    private boolean foregroundIsSetAt(int offset) {
        StyledDocument document = view.textPane().getStyledDocument();
        return document.getCharacterElement(offset).getAttributes().isDefined(StyleConstants.Foreground);
    }

    private Color colourAt(int offset) {
        StyledDocument document = view.textPane().getStyledDocument();
        return StyleConstants.getForeground(document.getCharacterElement(offset).getAttributes());
    }
}
