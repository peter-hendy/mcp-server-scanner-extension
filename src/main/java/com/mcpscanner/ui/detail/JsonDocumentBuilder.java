package com.mcpscanner.ui.detail;

import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyleContext;
import javax.swing.text.StyledDocument;
import java.awt.Color;
import java.awt.Font;
import java.util.EnumMap;
import java.util.Map;

final class JsonDocumentBuilder {

    static final int HIGHLIGHT_LIMIT = 256 * 1024;

    private final JsonTokenizer tokenizer = new JsonTokenizer();
    private final Map<JsonTokenKind, AttributeSet> styles = new EnumMap<>(JsonTokenKind.class);
    private final Font font;

    JsonDocumentBuilder(JsonPalette palette, Font font) {
        this.font = font;
        for (JsonTokenKind kind : JsonTokenKind.values()) {
            palette.colourOf(kind).ifPresent(colour -> styles.put(kind, foreground(colour)));
        }
    }

    StyledDocument build(String text) {
        DefaultStyledDocument document = new DefaultStyledDocument();
        applyFont(document);
        try {
            document.insertString(0, text, null);
        } catch (BadLocationException e) {
            throw new IllegalStateException(e);
        }
        if (text.length() <= HIGHLIGHT_LIMIT) {
            tokenizer.tokenize(text).forEach(token -> colour(document, token));
        }
        return document;
    }

    private void colour(DefaultStyledDocument document, JsonToken token) {
        AttributeSet style = styles.get(token.kind());
        if (style != null) {
            document.setCharacterAttributes(token.start(), token.length(), style, false);
        }
    }

    private void applyFont(DefaultStyledDocument document) {
        if (font != null) {
            Style base = document.getStyle(StyleContext.DEFAULT_STYLE);
            StyleConstants.setFontFamily(base, font.getFamily());
            StyleConstants.setFontSize(base, font.getSize());
        }
    }

    private static AttributeSet foreground(Color colour) {
        SimpleAttributeSet attributes = new SimpleAttributeSet();
        StyleConstants.setForeground(attributes, colour);
        return attributes;
    }
}
